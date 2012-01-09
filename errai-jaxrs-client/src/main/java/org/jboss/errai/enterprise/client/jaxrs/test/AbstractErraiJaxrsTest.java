/*
 * Copyright 2011 JBoss, a division of Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.enterprise.client.jaxrs.test;

import org.jboss.errai.bus.client.api.RemoteCallback;
import org.jboss.errai.enterprise.client.jaxrs.api.ResponseCallback;
import org.jboss.errai.enterprise.client.jaxrs.api.RestClient;

import com.google.gwt.http.client.Response;
import com.google.gwt.junit.client.GWTTestCase;

/**
 * Base class for Errai JAX-RS tests.
 * 
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public abstract class AbstractErraiJaxrsTest extends GWTTestCase {
  protected String jaxRsApplicationRoot = "/";
 
  @Override
  protected void gwtSetUp() throws Exception {
    RestClient.setApplicationRoot(jaxRsApplicationRoot);
    super.gwtSetUp();
  }

  protected class AssertionCallback<T> implements RemoteCallback<T> {
    private String msg;
    private T expected;

    public AssertionCallback(String msg, T expected) {
      this.msg = msg;
      this.expected = expected;
      delayTestFinish(5000);
    }

    @Override
    public void callback(T response) {
      assertEquals(msg, expected, response);
      finishTest();
    }
  }

  protected class AssertionResponseCallback implements ResponseCallback {
    private String msg;
    private int statusCode;
    private String body;

    public AssertionResponseCallback(String msg, int statusCode) {
      this.msg = msg;
      this.statusCode = statusCode;
      delayTestFinish(5000);
    }

    public AssertionResponseCallback(String msg, int statusCode, String body) {
      this(msg, statusCode);
      this.body = body;
    }

    @Override
    public void callback(Response response) {
      assertEquals(msg, statusCode, response.getStatusCode());
      if (body != null)
        assertEquals(msg, body, response.getText());
      finishTest();
    }
  }
}