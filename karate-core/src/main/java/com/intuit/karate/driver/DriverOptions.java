/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.driver;

import com.intuit.karate.Logger;
import com.intuit.karate.core.Engine;
import com.intuit.karate.driver.chrome.ChromeDevToolsDriver;
import com.intuit.karate.driver.chrome.ChromeWebDriver;
import com.intuit.karate.driver.edge.EdgeDevToolsDriver;
import com.intuit.karate.driver.edge.MicrosoftWebDriver;
import com.intuit.karate.driver.firefox.GeckoWebDriver;
import com.intuit.karate.driver.safari.SafariWebDriver;
import com.intuit.karate.driver.windows.WinAppDriver;
import com.intuit.karate.shell.CommandThread;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class DriverOptions {
    
    public static final long DEFAULT_TIMEOUT = 30 * 1000; // 30 seconds

    public final Map<String, Object> options;
    public final long timeout;
    public final boolean start;
    public final String executable;
    public final String type;
    public final int port;
    public final String host = "localhost";
    public final boolean headless;
    public final boolean showProcessLog;
    public final boolean showDriverLog;
    public final Logger logger;
    public final Logger processLogger;
    public final Logger driverLogger;
    public final String uniqueName;
    public final File workingDir;
    public final String workingDirPath;
    public final String processLogFile;
    public final List<String> args = new ArrayList();

    private <T> T get(String key, T defaultValue) {
        T temp = (T) options.get(key);
        return temp == null ? defaultValue : temp;
    }

    public DriverOptions(Map<String, Object> options, Logger logger, int defaultPort, String defaultExecutable) {
        this.options = options;
        this.logger = logger == null ? new Logger(getClass()) : logger;
        timeout = get("timeout", DEFAULT_TIMEOUT);
        type = get("type", null);
        port = get("port", defaultPort);
        start = get("start", false);
        executable = get("executable", defaultExecutable);
        headless = get("headless", false);
        showProcessLog = get("showProcessLog", false);
        uniqueName = type + "_" + System.currentTimeMillis();
        String packageName = getClass().getPackage().getName();
        processLogger = showProcessLog ? this.logger : new Logger(packageName + "." + uniqueName);
        showDriverLog = get("showDriverLog", false);
        driverLogger = showDriverLog ? this.logger : new Logger(packageName + "." + uniqueName);
        if (executable != null) {
            args.add(executable);
        }        
        workingDir = new File(Engine.getBuildDir() + File.separator + uniqueName);
        workingDirPath = workingDir.getAbsolutePath();
        processLogFile = workingDir.getPath() + File.separator + type + ".log";        
    }        
    
    public void arg(String arg) {
        args.add(arg);
    }

    public CommandThread startProcess() {
        if (executable == null) {
            return null;
        }
        CommandThread command = new CommandThread(processLogger, uniqueName, processLogFile, workingDir, args.toArray(new String[]{}));
        command.start();
        waitForPort(host, port);
        return command;
    }
    
    public static Driver construct(Map<String, Object> options, Logger logger) {
        String type = (String) options.get("type");
        if (type == null) {
            logger.warn("type was null, defaulting to 'chrome'");
            type = "chrome";
        }
        switch (type) {
            case "chrome":
                return ChromeDevToolsDriver.start(options, logger);
            case "msedge":
                return EdgeDevToolsDriver.start(options, logger);
            case "chromedriver":
                return ChromeWebDriver.start(options, logger);
            case "geckodriver":
                return GeckoWebDriver.start(options, logger);
            case "safaridriver":
                return SafariWebDriver.start(options, logger);
            case "mswebdriver":
                return MicrosoftWebDriver.start(options, logger);
            case "winappdriver":
                return WinAppDriver.start(options, logger);
            default:
                logger.warn("unknown driver type: {}, defaulting to 'chrome'", type);
                return ChromeDevToolsDriver.start(options, logger);
        }
    }  
    
    public String selectorScript(String id) {
        if (id.startsWith("^")) {
            id = "//a[text()='" + id.substring(1) + "']";
        } else if (id.startsWith("*")) {
            id = "//a[contains(text(),'" + id.substring(1) + "')]";
        }
        if (id.startsWith("/")) {
            return "document.evaluate(\"" + id + "\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue";
        }
        return "document.querySelector(\"" + id + "\")";
    }

    public void sleep(int millis) {
        if (millis == 0) {
            return;
        }
        try {
            processLogger.debug("sleeping for millis: {}", millis);
            Thread.sleep(millis);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean waitForPort(String host, int port) {
        int attempts = 0;
        do {
            SocketAddress address = new InetSocketAddress(host, port);
            try {
                processLogger.debug("poll attempt #{} for port to be ready - {}:{}", attempts, host, port);
                SocketChannel sock = SocketChannel.open(address);
                sock.close();
                return true;
            } catch (IOException e) {
                sleep(250);
            }
        } while (attempts++ < 3);
        return false;
    } 
    
    public Map<String, Object> newMapWithSelectedKeys(Map<String, Object> map, String... keys) {
        Map<String, Object> out = new HashMap(keys.length);
        for (String key : keys) {
            Object o = map.get(key);
            if (o != null) {
                out.put(key, o);
            }
        }
        return out;
    }    

}
