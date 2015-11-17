/*
 * Copyright (C) 2015 Ben Hamilton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zaxxer.nuprocess;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.sun.jna.Platform;
import com.zaxxer.nuprocess.NuProcess.Stream;

public class FastExitingProcessTest
{
   private static class Handler extends NuAbstractProcessHandler
   {
      public final ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
      public final WritableByteChannel stdoutBytesChannel = Channels.newChannel(stdoutBytes);
      public int exitCode = -1;
      public Exception stdoutException;

      @Override
      public void onStart(NuProcess nuProcess)
      {
         nuProcess.want(Stream.STDOUT);
      }

      @Override
      public void onExit(int exitCode)
      {
         this.exitCode = exitCode;
      }

      @Override
      public boolean onStdout(ByteBuffer buffer, boolean closed)
      {
         try {
            stdoutBytesChannel.write(buffer);
         }
         catch (Exception e) {
            stdoutException = e;
         }

         return !closed;
      }
   }

   @Test
   public void whenProcessWritesToStdoutThenExitsThenHandlerReceivesOutput() throws Exception
   {
      Handler handler = new Handler();
      NuProcess process;
      if (Platform.isWindows()) {
         // We can't use an argument with a space here because the following command:
         //   cmd /c echo "Hello world!"
         // literally echoes the quotes around "Hello world!" to stdout.
         process = new NuProcessBuilder(handler, "cmd.exe", "/c", "echo", "hello").start();
      }
      else {
         process = new NuProcessBuilder(handler, "echo", "hello").start();
      }

      TimeUnit.SECONDS.sleep(2);

      int retVal = process.waitFor(Long.MAX_VALUE, TimeUnit.SECONDS);

      assertThat("Process should exit cleanly", retVal, equalTo(0));
      assertThat("Process callback should indicate clean exit", handler.exitCode, equalTo(0));
      assertThat("No exceptions thrown writing to stdout", handler.stdoutException, is(nullValue()));
      assertThat("Stdout should contain expected output", handler.stdoutBytes.toString("UTF-8"), equalTo(String.format("hello%n")));
   }
}