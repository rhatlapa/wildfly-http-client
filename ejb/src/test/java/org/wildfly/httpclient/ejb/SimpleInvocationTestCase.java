/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.httpclient.ejb;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;

import javax.ejb.ApplicationException;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.httpclient.common.WildflyHttpContext;

import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
@RunWith(EJBTestServer.class)
public class SimpleInvocationTestCase {

    public static final String APP = "wildfly-app";
    public static final String MODULE = "wildfly-ejb-remote-server-side";
    public static final String BEAN = "EchoBean";
    public static final int RETRIES = 10;

    private String largeMessage;

    @Before
    public void before() {
        EJBTestServer.registerServicesHandler("common/v1/affinity", httpServerExchange -> httpServerExchange.getResponseHeaders().put(Headers.SET_COOKIE, "JSESSIONID=" + EJBTestServer.INITIAL_SESSION_AFFINITY));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; ++i) {
            sb.append("Hello World ");
        }
        largeMessage = sb.toString();
    }

    @Test
    public void testSimpleInvocationViaURLAffinity() throws Exception {
        for (int i = 0; i < RETRIES; ++i) {
            clearSessionId();
            EJBTestServer.setHandler((invocation, affinity, out, method, handle, attachments) -> {
                if (invocation.getParameters().length == 0) {
                    return "a message";
                } else {
                    return invocation.getParameters()[0];
                }
            });
            final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "");
            final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
            final String message = "Hello World!!!";
            EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(new URI(EJBTestServer.getDefaultServerURL())));
            String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", message, echo);
            echo = proxy.echo(largeMessage);
            Assert.assertEquals("Unexpected echo message", largeMessage, echo);

            String m = proxy.message();
            Assert.assertEquals("a message", m);
        }
    }

    /**
     * Tests that when a URL path that's constructed out of user configurable (like app-name, module-name,
     * bean-name, distinct-name etc...) parts and/or if the method being invoked consists of parameter type(s)
     * that can potentially contain characters which need to be encoded, then the path thus constructed
     * by this EJB HTTP client library is indeed encoded correctly, resulting in a proper invocation result
     * from the target EJB.
     *
     * @see <a href="https://issues.jboss.org/browse/WFLY-9788">WFLY-9788</a> for more details
     *
     * @throws Exception
     */
    @Test
    public void testSimpleInvocationWithURLNeedingEncoding() throws Exception {
        EJBTestServer.setHandler((invocation, affinity, out, methodLocator, handle, attachments) -> {
            // check the invoked method and make sure it maps correctly to the view interface's method
            final Method viewMethod = EchoRemote.class.getDeclaredMethod("echo", new Class[] {String[].class});
            if (!methodLocator.getMethodName().equals(viewMethod.getName())) {
                throw new RuntimeException("Unexpected method " + methodLocator.getMethodName());
            }
            if (methodLocator.getParameterCount() != viewMethod.getParameterCount()) {
                throw new RuntimeException("Unexpected method parameter count for method " + methodLocator.getMethodName());
            }
            final Class<?>[] expectedViewMethodParamTypes = viewMethod.getParameterTypes();
            for (int i = 0; i < expectedViewMethodParamTypes.length; i++) {
                if (!expectedViewMethodParamTypes[i].getName().equals(methodLocator.getParameterTypeName(i))) {
                    throw new RuntimeException("Unexpected method parameter type " + methodLocator.getParameterTypeName(i) + " expected " + expectedViewMethodParamTypes[i].getName());
                }
            }
            return invocation.getParameters()[0];
        });
        // locate a EJB through some "fancy" (yet valid) app/module/bean names
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, "foo:", "bar:hello;world", "Calculator;Bean", "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final String[] messages = new String[]{"Hello World!!!", "2018"};
        EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(new URI(EJBTestServer.getDefaultServerURL())));
        // invoke on a method which accepts array types
        final String[] echoes = proxy.echo(messages);
        Assert.assertArrayEquals("Unexpected echo message", messages, echoes);
    }

    @Test
    public void testSimpleSSLInvocationViaURLAffinity() throws Exception {
        for (int i = 0; i < RETRIES; ++i) {
            clearSessionId();
            EJBTestServer.setHandler((invocation, affinity, out, method, handle, attachments) -> {
                if (invocation.getParameters().length == 0) {
                    return "a message";
                } else {
                    return invocation.getParameters()[0];
                }
            });
            final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "");
            final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
            final String message = "Hello World!!!";
            EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(new URI(EJBTestServer.getDefaultSSLServerURL())));
            String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", message, echo);
            echo = proxy.echo(largeMessage);
            Assert.assertEquals("Unexpected echo message", largeMessage, echo);

            String m = proxy.message();
            Assert.assertEquals("a message", m);
        }
    }

    @Test
    public void testCompressedInvocation() throws Exception {
        for (int i = 0; i < RETRIES; ++i) {
            clearSessionId();
            EJBTestServer.setHandler((invocation, affinity, out, method, handle, attachments) -> "a message");
            final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "");
            final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
            EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(new URI(EJBTestServer.getDefaultServerURL())));
            String m = proxy.compressMessage();
            Assert.assertEquals("a message", m);
        }
    }

    @Test
    public void testFailedCompressedInvocation() throws Exception {
        for (int i = 0; i < RETRIES; ++i) {
            clearSessionId();
            EJBTestServer.setHandler((invocation, affinity, out, method, handle, attachments) -> {
                throw new RuntimeException("a message");
            });
            final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "");
            final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
            EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(new URI(EJBTestServer.getDefaultServerURL())));
            try {
                proxy.compressMessage();
            } catch (RuntimeException e) {
                Assert.assertEquals("a message", e.getMessage());
            }
        }
    }

    @Test
    public void testSimpleInvocationViaDiscovery() throws Exception {
        for (int i = 0; i < RETRIES; ++i) {
            clearSessionId();
            EJBTestServer.setHandler((invocation, affinity, out, method, handle, attachments) -> invocation.getParameters()[0]);
            final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "");
            final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
            final String message = "Hello World!!!";
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", message, echo);
        }
    }


    @Test(expected = TestException.class)
    public void testSimpleFailedInvocation() throws Exception {
        for (int i = 0; i < RETRIES; ++i) {
            clearSessionId();
            EJBTestServer.setHandler((invocation, affinity, out, method, handle, attachments) -> {
                throw new TestException(invocation.getParameters()[0].toString());
            });
            final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, APP, MODULE, BEAN, "");
            final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
            final String message = "Hello World!!!";
            for (int j = 0; j < 10; ++j) {
                String echo = proxy.echo(message);
                Assert.assertEquals("Unexpected echo message", message, echo);
            }
        }
    }

    @Test
    public void testInvocationAffinity() throws Exception {
        for (int i = 0; i < RETRIES; ++i) {
            clearSessionId();
            EJBTestServer.setHandler((invocation, affinity, out, method, handle, attachments) -> {
                out.setSessionAffinity("foo");
                return affinity;
            });
            final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, BEAN, "");
            final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);

            String echo = proxy.echo("");
            Assert.assertEquals("Unexpected echo message", EJBTestServer.INITIAL_SESSION_AFFINITY, echo);
            echo = proxy.echo("");
            Assert.assertEquals("Unexpected echo message", "foo", echo);
        }

    }


    @Test
    public void testSessionOpen() throws Exception {
        for (int i = 0; i < RETRIES; ++i) {
            clearSessionId();
            EJBTestServer.setHandler((invocation, affinity, out, method, handle, attachments) -> {
                StatefulEJBLocator<?> ejbLocator = (StatefulEJBLocator<?>) invocation.getEJBLocator();
                return new String(ejbLocator.getSessionId().getEncodedForm());
            });
            StatefulEJBLocator<EchoRemote> locator = EJBClient.createSession(EchoRemote.class, APP, MODULE, BEAN, "");
            EchoRemote proxy = EJBClient.createProxy(locator);
            final String message = "Hello World!!!";
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", "SFSB_ID", echo);
        }

    }

    @Test
    @Ignore
    public void testSessionOpenLazyAffinity() throws Exception {

        for (int i = 0; i < RETRIES; ++i) {
            clearSessionId();
            EJBTestServer.setHandler((invocation, affinity, out, method, handle, attachments) -> new String(Base64.getDecoder().decode(invocation.getEJBLocator().asStateful().getSessionId().getEncodedForm())) + "-" + affinity);

            StatefulEJBLocator<EchoRemote> locator = EJBClient.createSession(EchoRemote.class, APP, MODULE, BEAN, "");
            EchoRemote proxy = EJBClient.createProxy(locator);
            final String message = "Hello World!!!";
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", "SFSB_ID-lazy-session-affinity", echo);
        }
    }

    private void clearSessionId() throws URISyntaxException {
        WildflyHttpContext.getCurrent().getTargetContext(new URI(EJBTestServer.getDefaultServerURL())).clearSessionId();
    }

    @ApplicationException
    private static class TestException extends Exception {
        public TestException(String message) {
            super(message);
        }
    }
}
