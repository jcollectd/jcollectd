package org.jcollectd.server.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jcollectd.agent.protocol.Network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Httpd {

    private final static String _PREFIX = Network.KEY_PREFIX + "http.";

    private static final boolean ENABLED;

    private final static Integer backlog;
    private final static Integer queue;
    private final static Integer poolsize;
    private final static Integer poolsizemax;
    private final static Integer poolkeepalive;

    private static final InetSocketAddress INETADDR;

    static {
        ENABLED = Boolean.getBoolean(_PREFIX);
        INETADDR = new InetSocketAddress(System.getProperty(_PREFIX + "host", "127.0.0.1"),
                Integer.getInteger(_PREFIX + "port", Network.DEFAULT_PORT));

        backlog = Integer.getInteger("httpd.backlog", 0);
        queue = Integer.getInteger("httpd.poolq", 500);
        poolsize = Integer.getInteger("httpd.poolsize", 5);
        poolsizemax = Integer.getInteger("httpd.poolsizemax", 10);
        poolkeepalive = Integer.getInteger("httpd.poolkeepalive", 5);
    }

    private HttpServer httpd;

    public Httpd() throws IOException {

        ThreadPoolExecutor threadPool =
                new ThreadPoolExecutor(poolsize, poolsizemax, poolkeepalive, TimeUnit.SECONDS,
                        new ArrayBlockingQueue<Runnable>(queue));

        httpd = HttpServer.create(INETADDR, backlog);
        httpd.setExecutor(threadPool);
    }

    public final void start() {
        registerShutdownHook(httpd);
        httpd.start();
    }

    public final void addContext(String url, HttpHandler handler) {
        httpd.createContext(url, handler);
    }

    private static void registerShutdownHook(final HttpServer httpd) {
        Runtime.getRuntime().addShutdownHook(new Thread() {

            public void run() {
                httpd.stop(poolkeepalive);
            }
        });
    }

    public static void main(String[] args) throws IOException {
        Httpd httpd = new Httpd();
        httpd.addContext("/jcollectd", new CollectdPostHandler());
        httpd.start();
    }

    static class CollectdPostHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange he) throws IOException {
            long start = System.currentTimeMillis();
            try {

                if ("POST".equalsIgnoreCase(he.getRequestMethod())) {
                    handlePost(he);
                } else {
                    handleInvalidHttpMethod(he);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
            }
        }

        /**
         * PUTVAL "host/plugin-pluginInstance/type-typeInstance" interval=10 vl
         * PUTNOTIF "host=Host plugin=Plugin plugin_instance=PluginInstance type=Type type_instance=TypeInstance time=1201094702 severity=warning message=Message"
         */

        private static Pattern PUTVAL_PATTERN = Pattern.compile(
                "PUTVAL (?<host>[^/]+)/(?<plugin>[^/-]+)(-(?<pluginInstance>[^/]+))?/(?<type>[^ /-]+)(-(?<typeInstance>[^ ]+))?" +
                        "( interval=(?<interval>\\d+))?( (?<values>.+))");
        private static Pattern PUTNOTIF_PATTERN = Pattern.compile("PUTVAL ([^/]+)/([^/-]+)(-([^/]))/([^/-]+)(-([^/]))( interval=([d]+))( (.+))\n");


        private void handlePost(HttpExchange he) throws IOException {
            long start = System.currentTimeMillis();
            InputStream requestStream = he.getRequestBody();

            // TODO: nio?
            BufferedReader reader = new BufferedReader(new InputStreamReader(requestStream,
                    "UTF-8"));

            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[requestStream.available()];
            reader.read(buffer);
            Matcher matcher = PUTVAL_PATTERN.matcher(String.valueOf(buffer));

            while (matcher.find()) {
                System.out.print(" host=" + matcher.group("host"));
                System.out.print(" plugin=" + matcher.group("plugin"));
                System.out.print(" pluginInstance=" + matcher.group("pluginInstance"));
                System.out.print(" type=" + matcher.group("type"));
                System.out.print(" typeInstance=" + matcher.group("typeInstance"));
                System.out.print(" interval=" + matcher.group("interval"));
                System.out.println(" values=" + matcher.group("values"));
            }

            System.out.println("HTTP_POST " + buffer.length + " bytes in " + (System.currentTimeMillis() - start) + " ms");

            try {

            } finally {
                sendResponse(he);
                reader.close();
                requestStream.close();
            }
        }

        private void handleInvalidHttpMethod(HttpExchange he) throws IOException {
            String response = "405 Method not allowed";
            he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD,
                    response.length());

            OutputStream os = he.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private void sendResponse(HttpExchange httpExchange)
                throws IOException {
            OutputStream responseStream = httpExchange.getResponseBody();
            try {
                byte[] responseByteArray = "".getBytes();
                setHeaders(httpExchange.getResponseHeaders());
                httpExchange.sendResponseHeaders(HttpURLConnection.HTTP_OK,
                        responseByteArray.length);
                responseStream.write(responseByteArray);
                responseStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            } finally {
                responseStream.close();
            }
        }

        private void setHeaders(Headers headers) {
            headers.add("Last-Modified", DateFormatter.now());
            headers.add("Expires", "Mon, 26 Jul 1997 05:00:00 GMT");
            headers.add("Cache-Control", "no-store, no-cache, must-revalidate");
            headers.add("Cache-Control", "post-check=0, pre-check=0");
            headers.add("Pragma", "no-cache");
            headers.add("Content-type", "text/javascript; charset=utf-8");
        }
    }

    static class DateFormatter {

        private static final SimpleDateFormat dateFormatter;

        static {
            dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                    Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        public static String format(Date date) {
            return dateFormatter.format(date);
        }

        public static String now() {
            return format(new Date());
        }
    }
}
