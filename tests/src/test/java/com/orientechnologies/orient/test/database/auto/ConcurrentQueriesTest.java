/*
 * Copyright 2010-2012 Luca Garulli (l.garulli--at--orientechnologies.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.test.database.auto;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.orientechnologies.common.concur.ONeedRetryException;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.test.ConcurrentTestHelper;

@Test
public class ConcurrentQueriesTest {
  private final static int    THREADS      = 10;
  private final static int    CYCLES       = 50;
  private final static int    MAX_RETRIES  = 50;

  protected String            url;
  private ODatabaseDocumentTx db;
  private final AtomicLong    counter      = new AtomicLong();
  private final AtomicLong    totalRetries = new AtomicLong();

  class CommandExecutor implements Callable<Void> {

    String url;

    public CommandExecutor(String url) {
     this.url = url;
    }

    @Override
    public Void call() {
        for (int i = 0; i < CYCLES; i++) {
          ODatabaseDocumentTx db = new ODatabaseDocumentTx(url).open("admin", "admin");
          try {
            for (int retry = 0; retry < MAX_RETRIES; ++retry) {
              try {
                db.command(new OCommandSQL("select from Concurrent")).execute();

                counter.incrementAndGet();
                totalRetries.addAndGet(retry);
                break;
              } catch (ONeedRetryException e) {
              try {
                Thread.sleep(retry * 10);
              } catch (InterruptedException e1) {
                throw new RuntimeException(e1);
              }
              }
            }
          } finally {
            db.close();
          }
        }
      return null;
    }
  }

  @Parameters(value = "url")
  public ConcurrentQueriesTest(@Optional(value = "memory:test") String iURL) {
    url = iURL;
  }

  @BeforeClass
  public void init() {
    if ("memory:test".equals(url))
      new ODatabaseDocumentTx(url).create().close();

    db = new ODatabaseDocumentTx(url).open("admin", "admin");

    if (db.getMetadata().getSchema().existsClass("Concurrent"))
      db.getMetadata().getSchema().dropClass("Concurrent");

    db.getMetadata().getSchema().createClass("Concurrent");

    for (int i = 0; i < 1000; ++i) {
      db.newInstance("Concurrent").field("test", i).save();
    }
  }

  @AfterClass
  public void deinit() {
    if (!db.isClosed())
      db.close();
  }

  @Test
  public void concurrentCommands() throws Exception {
    System.out.println("Spanning " + THREADS + " threads...");

    ConcurrentTestHelper.test(THREADS, new ConcurrentTestHelper.TestFactory<Void>() {
      @Override
      public Callable<Void> createWorker() {
        return new CommandExecutor(url);
      }
    });

 System.out.println("Done! Total queries executed in parallel: " + counter.get() + " average retries: "
        + ((float) totalRetries.get() / (float) counter.get()));

    Assert.assertEquals(counter.get(), CYCLES * THREADS);
  }
}
