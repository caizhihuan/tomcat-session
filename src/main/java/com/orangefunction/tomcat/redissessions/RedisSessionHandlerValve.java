package com.orangefunction.tomcat.redissessions;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.SessionConfig;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.ServerCookie;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import java.io.IOException;


public class RedisSessionHandlerValve extends ValveBase {
    private final Log log = LogFactory.getLog(RedisSessionManager.class);
    private RedisSessionManager manager;

    public void setRedisSessionManager(RedisSessionManager manager) {
        this.manager = manager;
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        try {
            // this will cause Request.doGetSession to create the session cookie if necessary
            request.getSession(true);

            // replace any Tomcat-generated session cookies with our own
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
//                    log.debug("cookie name is =" + cookie.getName());
//                    log.debug("SessionConfig.getSessionCookieName(null) = " + SessionConfig.getSessionCookieName(null));
//                    log.debug("session cookie name is:" + request.getContext().getSessionCookieName());
                    if (SessionConfig.getSessionCookieName(request.getContext()).equals(cookie.getName())) {
                        //log.info("--------------------------------------");
//                        log.debug("replace cookie start");
                        replaceCookie(request, response, cookie);
                    }
                }
            }
            getNext().invoke(request, response);
        } finally {
            manager.afterRequest();
        }
    }

    protected void replaceCookie(Request request, Response response, Cookie cookie) {
        // copy the existing session cookie, but use a different domain (only if domain is valid)
        String cookieDomain = ".xueleyun.com";
        String path = "/";
        Cookie newCookie = new Cookie(cookie.getName(), cookie.getValue());
        newCookie.setDomain(cookieDomain);
        newCookie.setPath(path);
        newCookie.setMaxAge(cookie.getMaxAge());
        newCookie.setVersion(cookie.getVersion());
        if (cookie.getComment() != null) {
            newCookie.setComment(cookie.getComment());
        }
        newCookie.setSecure(cookie.getSecure());

        // if the response has already been committed, our replacement strategy will have no effect
        if (response.isCommitted()) {
            log.error("CrossSubdomainSessionValve: response was already committed!");
        }

        MimeHeaders mimeHeaders = request.getCoyoteRequest().getMimeHeaders();

        //log.info(mimeHeaders.toString());
        for (int i = 0, size = mimeHeaders.size(); i < size; i++) {
            log.info("mimeHeaders.getName(i)=" + mimeHeaders.getName(i));
            if (mimeHeaders.getName(i).equals("Set-Cookie")) {
                MessageBytes value = mimeHeaders.getValue(i);
                //log.info("value is :" + value);
                if (value.indexOf(cookie.getName()) >= 0) {
                    StringBuffer buffer = new StringBuffer();
                    ServerCookie.appendCookieValue(buffer, newCookie.getVersion(), newCookie.getName(), newCookie.getValue(), newCookie.getPath(),
                            newCookie.getDomain(), newCookie.getComment(), newCookie.getMaxAge(), newCookie.getSecure(), true);
                    //log.info("CrossSubdomainSessionValve: old Set-Cookie value: " + value.toString());
                    //log.info("CrossSubdomainSessionValve: new Set-Cookie value: " + buffer);
                    value.setString(buffer.toString());
                    //log.info("replace!!!!");
                }
            }
        }
    }
}
