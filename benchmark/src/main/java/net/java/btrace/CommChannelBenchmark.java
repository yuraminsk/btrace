/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package net.java.btrace;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.ProfilerFactory;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 *
 * @author jbachorik
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
public class CommChannelBenchmark {
    private DatagramSocket dgServer, dgClient;
    private InetAddress lhost;
    private HttpServer httpServer;
    private HttpClient httpClient;
    private HttpMethod httpMethod;
    private String message;
    private byte[] messageBuf;
    private int messageBufLen;
    private final int udpPort = 4321;
    private final int httpPort = 5432;

    @Setup
    public void setup() {
        try {
            dgServer = new DatagramSocket(udpPort);
            dgClient = new DatagramSocket();
            lhost = InetAddress.getLocalHost();

            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", httpPort), 300);
            httpServer.createContext("/rest", (he)->{
                String response = "This is the response";
                he.sendResponseHeaders(200, response.length());
                try (OutputStream os = he.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            httpServer.setExecutor(null);
            httpServer.start();

            httpMethod = new HttpMethodBase("http://localhost:" + httpPort + "/rest?" + message) {
                @Override
                public String getName() {
                    return "PUT";
                }
            };

            httpClient = new HttpClient();
            message = "Hello_world";
            messageBuf = message.getBytes();
            messageBufLen = messageBuf.length;
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    @TearDown
    public void tearDown() {
        httpServer.stop(1);
        dgServer.close();
        dgClient.close();
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testSendDatagram() {
        if (dgServer != null) {
            try {
                DatagramPacket dgp = new DatagramPacket(messageBuf, messageBufLen, lhost, udpPort);
                dgClient.send(dgp);
            } catch (IOException e) {}
        }
    }

    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void testSendHttp() {
        if (httpServer != null) {
            try {
                httpClient.executeMethod(httpMethod);
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .addProfiler(ProfilerFactory.getProfilerByName("perfasm"))
                .include(".*" + CommChannelBenchmark.class.getSimpleName() + ".*test.*")
                .build();

        new Runner(opt).run();
    }
}
