package org.apache.catalina.connector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.coyote.http11.Http11Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadPoolConnector implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Connector.class);

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_ACCEPT_COUNT = 10;
    private static final int DEFAULT_IDLE_THREAD_COUNT = 10;
    private static final int DEFAULT_MAX_THREAD_COUNT = 200;
    private static final int DEFAULT_KEEP_ALIVE_TIME = 20000;
    private static final int DEFAULT_MAX_CONNECTION_COUNT = 8192;

    private final ServerSocket serverSocket;
    private final ExecutorService pool;
    private boolean stopped;

    public ThreadPoolConnector() {
        this(DEFAULT_PORT, DEFAULT_ACCEPT_COUNT, DEFAULT_MAX_THREAD_COUNT);
    }

    public ThreadPoolConnector(final int port, final int acceptCount, final int maxThreads) {
        log.info("Thread Pool Created! Accept Count: {}, Max Threads: {}", acceptCount, maxThreads);
        this.serverSocket = createServerSocket(port, acceptCount);
        this.pool = createThreadPool(maxThreads);
        this.stopped = false;
    }

    private ServerSocket createServerSocket(final int port, final int acceptCount) {
        try {
            final int checkedPort = checkPort(port);
            final int checkedAcceptCount = checkAcceptCount(acceptCount);
            return new ServerSocket(checkedPort, checkedAcceptCount);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ExecutorService createThreadPool(int maxThreads) {
        return new ThreadPoolExecutor(DEFAULT_IDLE_THREAD_COUNT, maxThreads,
                                      DEFAULT_KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<>(DEFAULT_MAX_CONNECTION_COUNT));
    }

    public void start() {
        var thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
        stopped = false;
        log.info("Web Application Server started {} port.", serverSocket.getLocalPort());
    }

    @Override
    public void run() {
        // 클라이언트가 연결될때까지 대기한다.
        while (!stopped) {
            connect();
        }
    }

    private void connect() {
        try {
            process(serverSocket.accept());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void process(final Socket connection) {
        if (connection == null) {
            return;
        }
        var processor = new Http11Processor(connection, new CatalinaAdapter());
        pool.submit(processor);
    }

    public void stop() {
        stopped = true;
        try {
            shutdownAndAwaitTermination(pool);
            serverSocket.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private int checkPort(final int port) {
        final var MIN_PORT = 1;
        final var MAX_PORT = 65535;

        if (port < MIN_PORT || MAX_PORT < port) {
            return DEFAULT_PORT;
        }
        return port;
    }

    private int checkAcceptCount(final int acceptCount) {
        return Math.max(acceptCount, DEFAULT_ACCEPT_COUNT);
    }

    void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Pool did not terminate");
                }
            }
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}