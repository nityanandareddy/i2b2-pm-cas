/**
 * Copyright (c) 2010 University of Kansas Medical Center (kumc.edu).
 * @todo: determine license terms.
 */
package edu.harvard.i2b2.pm.delegate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;

import edu.harvard.i2b2.common.exception.I2B2DAOException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.pm.dao.PMDbDao;
import edu.harvard.i2b2.pm.datavo.pm.UserType;
import edu.harvard.i2b2.pm.ws.ServicesMessage;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Properties;

/**
 * ServicesHandlerCAS validates users using
 * JA-SIG Central Authentication Service (CAS).
 *
 * @author: Dan Connolly
 *
 * @param: service: CAS service URL is passed in place of a username
 * @param: ticket: CAS ticket is passed in place of a password
 *
 * @todo: Change hard-coded CAS server address to a deploy-time option.
 */
public class ServicesHandlerCAS extends ServicesHandler {
    private static final String CONFIG_PATHNAME="/etc/eureka/application.properties";
    private static final String CAS_URL_PROPERTY_NAME = "cas.url";
    private static final String CAS_DEFAULT_URL = "https://localhost:8443/cas-server/";
    private static final Properties appProperties = new Properties();
    static {
        try {
            FileReader fr = new FileReader(CONFIG_PATHNAME);
            appProperties.load(fr);
            String readCasUrl = appProperties.getProperty(CAS_URL_PROPERTY_NAME);
            if (readCasUrl == null) {
                appProperties.setProperty(CAS_URL_PROPERTY_NAME, CAS_DEFAULT_URL);
            } else if (!readCasUrl.endsWith("/")) {
                appProperties.setProperty(CAS_URL_PROPERTY_NAME, readCasUrl + "/");
            }
            fr.close();
            fr = null;
        } catch (FileNotFoundException ex) {
            appProperties.setProperty(CAS_URL_PROPERTY_NAME, CAS_DEFAULT_URL);
        } catch (IOException ex) {
            throw new IllegalStateException("Error reading CAS integration configuration file " + CONFIG_PATHNAME, ex);
        }
    }

    protected static Exception fail = new Exception("Username or password does not exist");


    public ServicesHandlerCAS(ServicesMessage servicesMsg) throws I2B2Exception{
	super(servicesMsg);
    }

    protected UserType validateSuppliedPassword (String service, 
            String ticket, Hashtable param) throws Exception {

	// support password-based accounts too for OBFSC_SERVICE_ACCOUNT
	if (! (service.startsWith("http:")
	       || service.startsWith("https:"))){
	    return super.validateSuppliedPassword(service, ticket, param);
	}

	String addr = appProperties.getProperty(CAS_URL_PROPERTY_NAME) + "serviceValidate?"
	    + "service=" + URLEncoder.encode(service, "UTF-8")
	    + "&ticket=" + URLEncoder.encode(ticket, "UTF-8");
	log.debug("CAS validation address: " + addr);

	BufferedReader body = URLOpener.open(addr);
        try {
	    StringBuilder builder = new StringBuilder();
	    String line;
	    while ((line = body.readLine()) != null) {
		builder.append(line);
	    }
	    String response = builder.toString();
	    int start = response.indexOf("<cas:authenticationSuccess>");
	    String username;
	    if (start > -1) {
		start += "<cas:authenticationSuccess>".length();
		start = response.indexOf("<cas:user>", start);
		if (start < 0) {
		    log.error("Unexpected response from CAS: " + response);
		    throw fail;
		} else {
		    start += "<cas:user>".length();
		    int finish = response.indexOf("</cas:user>", start);
		    if (finish < 0) {
			log.error("Unexpected response from CAS: " + response);
			throw fail;
		    } else {
			username = response.substring(start, finish).trim();
		    }
		}
	    } else {
		if (response.contains("<cas:authenticationFailure>")) {
		    log.debug("CAS authentication result negative");
		} else {
		    log.error("Unexpected response from CAS: " + response);
		}
		
                throw fail;
	    }

            log.debug("CAS authenticated user:" + username);

            PMDbDao pmDb = new PMDbDao();
            List answers;
            try {
                answers = pmDb.getUser(username, null);
            } catch (I2B2DAOException dberr) {
                log.debug(dberr.toString());
                throw fail;
            }

            Iterator users = answers.iterator();
            if (!users.hasNext()) {
                log.debug("No such user record: " + username);
                throw fail;
            }
            body.close();
            body = null;
            return (UserType)users.next();
        } finally {
            if (body != null) {
                try {
                    body.close();
                } catch (IOException ignore) {}
            }
        }
    }
}

/**
 * a la python's urlopener
 * Note: assumes utf-8
 */
class URLOpener {
    public static BufferedReader open(String addr)
	throws java.net.MalformedURLException, java.io.IOException {
	URLConnection conn = new java.net.URL(addr).openConnection();
	return new BufferedReader(new InputStreamReader(conn.getInputStream(),
							"utf-8"));
    }
}
