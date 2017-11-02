package ch.cern.eos;

import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.conf.Configuration;

import sun.security.krb5.EncryptionKey;
import sun.security.krb5.KrbException;
import sun.security.krb5.internal.KerberosTime;
import sun.security.krb5.internal.Ticket;
import sun.security.krb5.internal.TicketFlags;
import sun.security.krb5.internal.ccache.Credentials;
import sun.security.krb5.internal.ccache.CredentialsCache;
import sun.security.krb5.internal.ccache.CCacheInputStream;
import sun.security.krb5.internal.ccache.CCacheOutputStream;
import sun.security.krb5.internal.ccache.FileCCacheConstants;
import sun.security.krb5.internal.ccache.FileCredentialsCache;
import sun.security.krb5.PrincipalName;

import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.Files;
import java.io.File;
import java.util.Arrays;

import java.lang.System;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.UnsatisfiedLinkError;
import java.lang.reflect.Method;

import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;

public class EOSKrb5
{
    public static String krb5ccname="";
    public static int hasKrbToken = -1;
    public static int hasKrbTGT = -1;

    private static UserGroupInformation ugi;

    private static String tokenKind = "krb5";

    private static int executor = 0;
    private static EOSDebugLogger eosDebugLogger;

    public synchronized static String setKrb() {
        eosDebugLogger = new EOSDebugLogger(System.getenv("EOS_debug") != null);
        
        // if no Krb ticket, set from Token. If no Krb Token, set from ticket
        int hadKrbTGT = hasKrbTGT, hadKrbToken = hasKrbToken;
        if (hasKrbToken < 0 && hasKrbTGT < 0) {
            checkToken();	    // check for token if still initial state
        }

        if (hasKrbToken > 0 && executor==1) {			// we're most likely a M/R task or Spark executor
            if (hasKrbTGT > -10) {
                try {
                    krb5ccname = setKrbTGT();
                } catch (IOException | KrbException e) {
                    eosDebugLogger.print("setKrbTGT: " + e.getMessage());
                    eosDebugLogger.printStackTrace();
                    hasKrbTGT -= 1;
                }
            }
        } else if (hasKrbTGT != 0 && executor==0) {		// we either have a Krb TGT or don't know yet
            try {
                setKrbToken();
            } 
            catch(IOException | KrbException e) {
                eosDebugLogger.print("setKrbToken: " + e.getMessage());
                eosDebugLogger.printStackTrace();
            }
        }
        else if (executor==1)
        {
            try {
                krb5ccname = setKrbTGT();
            } 
            catch(IOException | KrbException e) {
                eosDebugLogger.print("setKrbTGT: " + e.getMessage());
                eosDebugLogger.printStackTrace();
                hasKrbTGT -= 1;
            }
        }

        eosDebugLogger.print("setKrb: hasKrbToken " + hasKrbToken + "(" + hadKrbToken + ") hasKrbTGT " + hasKrbTGT + "(" + hadKrbTGT + ")"); 
        return krb5ccname;
    }

    private static boolean checkTGT()
    {
        // Check if Kerberos Cache is available locally
        String ccname = null;
        try {
           ccname =  System.getenv("KRB5CCNAME");
        }
        catch (UnsatisfiedLinkError e) {
            return false;  
        }

        CredentialsCache ncc;
        sun.security.krb5.Credentials crn;

        if (ccname == null) {
            return false;
        }
        if (ccname.length() > 5 && ccname.regionMatches(true, 0,  "FILE:", 0, 5)) {
            ccname = ccname.substring(5);
        }
        else {
            return false;
        }

        // testing if the krb cache is valid
        if (!new File(ccname).isFile()) {
            return false;
        }
        krb5ccname = ccname;
        return true;
    }

    //Checks if KRB cache exists in the token cache
    private static void checkToken() {
        try {
            ugi = UserGroupInformation.getLoginUser();
        } catch (IOException e) {
            eosDebugLogger.print("IOException in checkToken");
        }

	    boolean found = false;
        int i=0;

        for (Token<? extends TokenIdentifier> t : ugi.getTokens()) {
            eosDebugLogger.print("checkToken: found token " + t.getKind().toString());
            if (t.getKind().toString().equals(tokenKind)) {
                found = true;
            }

            if (t.getKind().toString().equals("HDFS_DELEGATION_TOKEN")) {
                executor=1;
            }
            i++;
        }

        eosDebugLogger.print("Total tokens found: " + i);
        if (found) {
            hasKrbToken = 1;
        }

        /* either no Krb token, or called too early? Leave it at current (limbo) state */
        return;
    }

    /* Save Kerberos TGT in Token cache so it gets propagated to YARN applications */
    public static void setKrbToken() throws IOException, KrbException {
        if (hasKrbToken > 0) {
            // Already set, perhaps because this is the M/R task
            return;
        }
        PrincipalName client;
        Credentials cccreds;

	    int cc_version = FileCCacheConstants.KRB5_FCC_FVNO_3;

        if (ugi.hasKerberosCredentials()) {	/* set up by checkToken */
            try {
                Method getTGT = ugi.getClass().getDeclaredMethod("getTGT");	/* , (Class<UserGroupInformation>) null); */
                getTGT.setAccessible(true);
                KerberosTicket TGT = (KerberosTicket) getTGT.invoke(ugi);	/*, (Class<UserGroupInformation>) null); */
                eosDebugLogger.print("got TGT for " + ugi);

                KerberosPrincipal p = TGT.getClient();
                client = new PrincipalName(p.getName(), p.getNameType());
                cccreds = new Credentials(client, new PrincipalName(TGT.getServer().toString()), new EncryptionKey(TGT.getSessionKeyType(),TGT.getSessionKey().getEncoded()),
                        new KerberosTime(TGT.getAuthTime()), new KerberosTime(TGT.getStartTime()), new KerberosTime(TGT.getEndTime()), new KerberosTime(TGT.getRenewTill()),
                        false, new TicketFlags(TGT.getFlags()), null, null, new Ticket(TGT.getEncoded()), null);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                eosDebugLogger.printStackTrace(e);
            }
        } 

        String krb5ccname;

        if (cccreds == null) {
            CredentialsCache ncc = CredentialsCache.getInstance();
            if (ncc == null) {
                throw new IOException("Found no valid credentials cache"); 
            }

            krb5ccname = ncc.cacheName();
            cccreds = ncc.getDefaultCreds();
            if (cccreds == null) {
                throw new IOException("No valid Kerberos ticket in credentials cache");
            }
            client = ncc.getPrimaryPrincipal();
        }

        if (krb5ccname == null) {
            krb5ccname = System.getenv("KRB5CCNAME");
            if (krb5ccname != null && krb5ccname.length() > 5 && krb5ccname.regionMatches(true, 0,  "FILE:", 0, 5)) {
                krb5ccname = krb5ccname.substring(5);	
                eosDebugLogger.print("krb5ccname filename " + krb5ccname);
                if (eosDebugLogger.isDebugEnabled()) { 
                    BufferedReader ir = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(new String[]{"ls", "-l", krb5ccname}).getInputStream()));
                    String ll; 
                    while ((ll = ir.readLine()) != null) { 
                        System.out.println(ll); 
                    }
                }
            } else {
                krb5ccname = Files.createTempFile("krb5", null).toString();
                eosDebugLogger.print("created krb5ccname " + krb5ccname);
            }
        }
        //saving  new cache location
        EOSKrb5.krb5ccname = krb5ccname;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(16384);
        CCacheOutputStream ccos = new CCacheOutputStream(bos);

        /* written: version, client_principal, default_creds */
        ccos.writeHeader(client, cc_version);
        ccos.addCreds(cccreds);
        ccos.close();

        byte [] krb5cc = bos.toByteArray();
        bos.reset();

        Krb5TokenIdentifier k5id = new Krb5TokenIdentifier();
        DataOutputStream dos = new DataOutputStream(bos);
        k5id.write(dos);
        dos.close();

        eosDebugLogger.print("setKrbToken saving krb5 ticket l=" + krb5cc.length + " in identifier l=" + bos.toByteArray().length);
        Token<? extends TokenIdentifier> t = new Token<Krb5TokenIdentifier>(bos.toByteArray(), krb5cc, new Text("krb5"), new Text("Cerberus service"));
        if (eosDebugLogger.isDebugEnabled()) {
            try { 
                t.renew(new Configuration());
            } catch (Exception e) {
                eosDebugLogger.print("setKrbToken failed to renew " + t.toString() + ": " + e);
            }
        } 

        hasKrbTGT = 1;
        if (!ugi.addToken(t)) {
            hasKrbToken = 0;
            eosDebugLogger.print("setKrbToken failed to add token " + t.toString());
            throw new KrbException("could not add token " + t.toString());
        } else {
            hasKrbToken = 1;
        }
    }

    /* Recover TGT from Token cache and set up krb5 crdentials cache */
    public static String setKrbTGT() throws IOException, KrbException {
        if (hasKrbTGT==1) {
            return krb5ccname;
        }

    	String krb5ccname = EOSFileSystem.getenv("KRB5CCNAME");
        if (krb5ccname != null && krb5ccname.length() > 5 && krb5ccname.regionMatches(true, 0,  "FILE:", 0, 5)) {
	        krb5ccname = krb5ccname.substring(5);
	        eosDebugLogger.print("krb5ccname filename " + krb5ccname);
                
            // if file exists we do not need to extract TGT from token
            // TBD: this check should be improved with ticket validity check 
            if (new File(krb5ccname).isFile()) {
                hasKrbTGT = 1;
                return krb5ccname; //TBD: should be timely renewed
            }
        } else {
            krb5ccname = Files.createTempFile("krb5", null).toString();
            eosDebugLogger.print("created krb5ccname " + krb5ccname);
        }
        //store the future location of TGT
        EOSKrb5.krb5ccname=krb5ccname;

        Token<? extends TokenIdentifier> tok = null;

        for (Token<? extends TokenIdentifier> t : ugi.getTokens()) {
            if (t.getKind().toString().equals(tokenKind)) {
                tok = t;
                eosDebugLogger.print("setKrbTGT found " + t);
                break;
            }
        }

        if (tok == null) {
            hasKrbToken = 0;
            throw new KrbException("setKrbTGT: no valid Krb Token");
        }
	    
        Configuration conf = new Configuration();

        /* explicit renewal instead of "if (tok.isManaged()) tok.renew()": need the krb5ccname */
        Krb5TokenRenewer renewer = new Krb5TokenRenewer();
        long lifeTime = renewer.renew(tok, conf);
    
        eosDebugLogger.print("setKrbTGT lifeTime " + lifeTime + " cache " + krb5ccname);
        hasKrbTGT = 1;
        return krb5ccname;
    }    
}