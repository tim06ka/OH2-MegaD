package ru.ittim.oh2.megad.core;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Timofey on 18.10.2016 20:27.
 */
public class MegaD {
    private final String host;
    private final String password;
    private final int listenPort;
    private final CloseableHttpClient client;
    private final MegaMediator mediator;


    public MegaD(String host, String password, int listenPort, int serverSoTimeout) {
        this.host = host;
        this.password = password;
        this.listenPort = listenPort;
        client = HttpClients.createDefault();

        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(serverSoTimeout)
                .setTcpNoDelay(true)
                .build();

        mediator = new MegaMediator(host, listenPort);
        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(listenPort)
                .setServerInfo("Test/1.1")
                .setSocketConfig(socketConfig)
//                // TODO: 27.11.2016 Корректный логгер
//                .setExceptionLogger(new HttpFileServer.StdErrorExceptionLogger())
                .registerHandler("*", mediator)
                .create();

        ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
        singleExecutor.submit(() -> {
            try {
                server.start();
                server.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } catch (IOException | InterruptedException e) {
                //// TODO: 23.10.2016 Обработку вовне
//                logger.error("Http server corrupted.", e);
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.shutdown(5, TimeUnit.SECONDS);
            }
        });
    }

    /**
     * Get channel status from ru.ittim.oh2.megad.core.MegaD device (ON or OFF)*
     * @param channel ru.ittim.oh2.megad.core.MegaD channel
     * @return channel's status
     */
    public PortSwitchStatus getSwitchStatus(int channel) {
        PortSwitchStatus status = null;
        try {
            String GET = "get";
            String body = getBody(GET, channel);
            if (body != null) {
                status = PortSwitchStatus.valueOf(body);
            }
        } catch (URISyntaxException | IOException ignored) {
            // TODO: 03.01.2017
        }
        if (status != null) {
            // TODO: 03.01.2017
        }
        return status;
    }

    /**
     * Turn on given channel
     * @param channel ru.ittim.oh2.megad.core.MegaD channel
     * @throws IOException
     * @throws URISyntaxException
     */
    public void turnOn(int channel) throws IOException, URISyntaxException {
        String ON = ":1";
        turnX(channel, ON);
    }

    /**
     * Turn off given channel
     * @param channel ru.ittim.oh2.megad.core.MegaD channel
     * @throws IOException
     * @throws URISyntaxException
     */
    public void turnOff(int channel) throws IOException, URISyntaxException {
        String OFF = ":0";
        turnX(channel, OFF);
    }

    /**
     * Switch given channel
     * @param channel ru.ittim.oh2.megad.core.MegaD channel
     * @throws IOException
     * @throws URISyntaxException
     */
    public void switchState(int channel) throws IOException, URISyntaxException {
        switch (getSwitchStatus(channel)) {
            case ON:
                turnOff(channel);
                break;
            case OFF:
                turnOn(channel);
                break;
            default:
                // TODO: 03.01.2017
        }
    }

    /**
     * Set ru.ittim.oh2.megad.core.MegaD channel to a given state
     *
     * @param channel channel on ru.ittim.oh2.megad.core.MegaD
     * @param cmd     command for channel (ON or OFF command)
     * @throws IOException
     * @throws URISyntaxException
     */
    private void turnX(int channel, String cmd) throws IOException, URISyntaxException {
        getBody(channel + cmd, channel);
    }

    /**
     * Execute given command on given channel on Megad, parse response and return body of response as string
     *
     * @param command command to ru.ittim.oh2.megad.core.MegaD
     * @param channel channel on ru.ittim.oh2.megad.core.MegaD
     * @return parsed response
     * @throws URISyntaxException in case of a command is incorrect
     * @throws IOException        in case of a problem or the connection was aborted
     */
    private String getBody(String command, int channel) throws URISyntaxException, IOException {
        CloseableHttpResponse response = null;
        try {
            HttpGet get = formGet(command, channel);
            response = client.execute(get);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    // TODO: 03.01.2017
                }
            }
        }
        return null;
    }

    /**
     * Form message for sending to ru.ittim.oh2.megad.core.MegaD
     *
     * @param command command to ru.ittim.oh2.megad.core.MegaD
     * @param channel channel for given command
     * @return get request for sending to ru.ittim.oh2.megad.core.MegaD
     * @throws URISyntaxException in case of a command is incorrect
     */
    private HttpGet formGet(String command, int channel) throws URISyntaxException {
        String SCHEME = "http";
        String CHANNEL = "pt";
        String CMD = "cmd";
        URI uri = new MegaDURIBuilder()
                .setScheme(SCHEME)
                .setHost(host)
                .setPath("/" + password)
                .setParameter(CHANNEL, String.valueOf(channel))
                .setParameter(CMD, command)
                .build();
        return new HttpGet(uri);
    }

    /**
     * Add information about device and channel for given message
     *
     * @param channel number of ru.ittim.oh2.megad.core.MegaD channel
     * @param message given message
     * @return message with extra info
     */
    private String getInfo(int channel, String message) {
        return "Device: " + this.toString() + "; Channel: " + String.valueOf(channel) +
                "; Message: " + message;
    }

    public void addListener(MegaListener listener) {
        mediator.addListener(listener);
    }

    public void removeListener(MegaListener listener) {
        mediator.removeListener(listener);
    }

    public void removeAllListeners() {
        mediator.removeAllListeners();
    }

    @Override
    public String toString() {
        return "MegaD{" +
                "host='" + host + '\'' +
                ", listenPort=" + listenPort +
                '}';
    }
}
