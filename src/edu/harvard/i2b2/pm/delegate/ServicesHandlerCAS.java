/**
 * Copyright (c) 2010 University of Kansas Medical Center (kumc.edu).
 * @todo: determine license terms.
 */
package edu.harvard.i2b2.pm.delegate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.InvalidParameterException;
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

import javax.servlet.http.HttpServletRequest;

import org.apache.axis2.context.MessageContext;

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
    private static final String I2B2_SERVICES_URL_NAME = "i2b2.services.url";
    private static final String I2B2_SERVICES_DEFAULR_URL = "http://localhost:9090/";
    private static final String CAS_DEFAULT_URL = "https://localhost:8443/cas-server/";
    private static final Properties appProperties = new Properties();
    
    static {
        try {
            FileReader fr = new FileReader(CONFIG_PATHNAME);
            appProperties.load(fr);
            String readCasUrl = appProperties.getProperty(CAS_URL_PROPERTY_NAME);
            String i2b2ServicesUrl = appProperties.getProperty(I2B2_SERVICES_URL_NAME);
            if (readCasUrl == null) {
                appProperties.setProperty(CAS_URL_PROPERTY_NAME, CAS_DEFAULT_URL);
            } else if (!readCasUrl.endsWith("/")) {
                appProperties.setProperty(CAS_URL_PROPERTY_NAME, readCasUrl + "/");
            }
            if(i2b2ServicesUrl == null)
            {
            	 appProperties.setProperty(I2B2_SERVICES_URL_NAME, I2B2_SERVICES_DEFAULR_URL);
            }else if (!i2b2ServicesUrl.endsWith("/")) {
            	appProperties.setProperty(I2B2_SERVICES_URL_NAME, i2b2ServicesUrl + "/");
            	
            	 /*log.error("Missing the value for property "+I2B2_SERVICES_URL_NAME+" in"+CONFIG_PATHNAME+".Please configure it.");
            	throw new InvalidParameterException("Missing the value for "+I2B2_SERVICES_URL_NAME+" property in"+CONFIG_PATHNAME+".Please configure it.");*/
            }
            fr.close();
            fr = null;
        } catch (FileNotFoundException ex) {
            appProperties.setProperty(CAS_URL_PROPERTY_NAME, CAS_DEFAULT_URL);
        } catch (IOException ex) {
            throw new IllegalStateException("Error reading CAS integration configuration file " + CONFIG_PATHNAME, ex);
        }
    }

    private MessageContext context = null;

    public ServicesHandlerCAS(ServicesMessage servicesMsg) throws I2B2Exception{
	super(servicesMsg);
	context = MessageContext.getCurrentMessageContext();
    }

    protected UserType validateSuppliedPassword (String service, 
            String ticket, Hashtable param) throws Exception {
    	
	// Support password-based accounts too for OBFSC_SERVICE_ACCOUNT and other clients.
        if (service != null && !service.isEmpty()) {
	    return super.validateSuppliedPassword(service, ticket, param);
        }
    
        HttpServletRequest request = (HttpServletRequest) context.getProperty("transport.http.servletRequest");
        System.out.println("request.getRequestURL():"+request.getRequestURL());
        System.out.println("request.getQueryString():"+request.getQueryString());

	if (request != null) {
	    ticket = request.getParameter("ticket");
	}
	System.out.println("getPropertyNames();"+context.getPropertyNames() );
	String addr = appProperties.getProperty(CAS_URL_PROPERTY_NAME) + "proxyValidate?"
	    //+ "service=" + URLEncoder.encode(appProperties.getProperty(I2B2_SERVICES_URL_NAME) +request.getRequestURI().toString(), "UTF-8")
		+ "service=" + URLEncoder.encode(appProperties.getProperty(I2B2_SERVICES_URL_NAME), "UTF-8")
	    + "&ticket=" + URLEncoder.encode(ticket, "UTF-8");
	log.info("CAS validation address: " + addr);
	System.out.println("+++++addr+++++"+addr);
	System.out.println("appProperties.getProperty(I2B2_SERVICES_URL_NAME)"+appProperties.getProperty(I2B2_SERVICES_URL_NAME));
	
	BufferedReader body = URLOpener.open(addr);
        try {
	    StringBuilder builder = new StringBuilder();
	    String line;
	    while ((line = body.readLine()) != null) {
		builder.append(line);
	    }
	    String response = builder.toString();
	    System.out.println("Response:+"+response);
	    log.info("Response:+"+response);
	    int start = response.indexOf("<cas:authenticationSuccess");
	    String username;
	    if (start > -1) {
	    	start = response.indexOf(">", start);
	    	if (start < 0) {
	    	    log.error("Unexpected response from CAS: " + response);
	    	    throw new Exception("EINTERNAL");
	    	}
		start += 1;
		start = response.indexOf("<cas:user", start);
		if (start < 0) {
		    log.error("Unexpected response from CAS: " + response);
		    throw new Exception("EINTERNAL");
		} else {
		    start = response.indexOf(">", start);
		    if (start < 0) {
	    	        log.error("Unexpected response from CAS: " + response);
	    	        throw new Exception("EINTERNAL");
	    	    }
		    start += 1;
		    int finish = response.indexOf("</cas:user", start);
		    if (finish < 0) {
			log.error("Unexpected response from CAS: " + response);
			throw new Exception("EINTERNAL");
		    } else {
			username = response.substring(start, finish).trim();
		    }
		}
	    } else {
		if (response.contains("<cas:authenticationFailure")) {
		    log.debug("CAS authentication result negative");
		    throw new Exception("EAUTHENTICATION");
		} else {
		    log.error("Unexpected response from CAS: " + response);
		    throw new Exception("EINTERNAL");
		}
	    }

            log.debug("CAS authenticated user:" + username);

            PMDbDao pmDb = new PMDbDao();
            List answers;
            try {
                answers = pmDb.getUser(username, null);
            } catch (I2B2DAOException dberr) {
                log.debug(dberr.toString());
                throw new Exception("EINTERNAL");
            }

            Iterator users = answers.iterator();
            if (!users.hasNext()) {
                log.debug("No such user record: " + username);
                throw new Exception("EAUTHORIZATION");
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
