package org.mickey;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 上传依赖到 Maven 私服
 */
public class Application {
    private static final Pattern DATE_PATTERN = Pattern.compile("-[\\d]{8}\\.[\\d]{6}-");
    private static final Runtime CMD = Runtime.getRuntime();
    private static final ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    private static String MAVEN_IMPORT_REPOSITORY = "C:\\Users\\Administrator\\Downloads\\protobuf-3.17.3\\protobuf-3.17.3\\";
    private static final String ERROR_LOG = "./deploy-error.log";
    private static final String RUNNING_LOG = "./running.log";
    /**
     * maven 本地 settings 配置
     */
    private static String SETTINGS_PATH = "C:\\Users\\Administrator\\.m2\\settings.xml";
    /**
     * maven 上传文件的远程仓库地址
     */
    private static String MAVEN_REMOTE_REPOSITORY = "http://192.168.122.163:8081/repository/maven-local/";
    /**
     * maven settings 中配置的仓库 id
     */
    private static String REPOSITORY_ID = "maven-local";
    /**
     * maven 上传 jar 文件的 cmd 命令
     */
    public static final String BASE_CMD = (System.getProperty("os.name").startsWith("Windows") ? "cmd /c" : "sh") +
            " mvn" + " -s " + SETTINGS_PATH + " deploy:deploy-file" + " -Durl=" + MAVEN_REMOTE_REPOSITORY +
            " -DrepositoryId=" + REPOSITORY_ID + " -DgeneratePom=false";

    public static void main(String[] args) {
        log("============================= MAVEN BATCH IMPORT APPLICATION RUNNING =============================");
        log("  ");
        log("============================= Loading Properties                     =============================");
        final Properties properties = getProperties();
        MAVEN_IMPORT_REPOSITORY = properties.getProperty("mvn.source.repository");
        log(String.format("%s: %s", "MAVEN_IMPORT_REPOSITORY", MAVEN_IMPORT_REPOSITORY));
        SETTINGS_PATH = properties.getProperty("mvn.settings");
        log(String.format("%s: %s", "SETTINGS_PATH", "SETTINGS_PATH"));
        MAVEN_REMOTE_REPOSITORY = properties.getProperty("mvn.remote.repository");
        log(String.format("%s: %s", "MAVEN_REMOTE_REPOSITORY", MAVEN_REMOTE_REPOSITORY));
        REPOSITORY_ID = properties.getProperty("mvn.remote.repository.id");
        log(String.format("%s: %s", "REPOSITORY_ID", REPOSITORY_ID));

        log("  ");
        log("============================= Do Importing...                        =============================");
        log("  ");
        deploy(Objects.requireNonNull(new File(MAVEN_IMPORT_REPOSITORY).listFiles()));
        EXECUTOR_SERVICE.shutdown();
    }

    private static Properties getProperties() {
        File file = new File("./application.properties");
        if (!file.exists()) {
            file = new File("src/main/resources/application.properties");
            if (!file.exists())
            {
                throw new RuntimeException("读取不到配置文件 application.properties");
            }
        }
        try (FileInputStream inputStream = new FileInputStream(file)) {
            final Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Deprecated
    public static boolean checkArgs(String[] args) {
        if (args.length != 1) {
            System.out.println("用法如： java -jar Deploy D:\\some\\path\\");
            return false;
        }
        File file = new File(args[0]);
        if (!file.exists()) {
            System.out.println(args[0] + " 目录不存在!");
            return false;
        }
        if (!file.isDirectory()) {
            System.out.println("必须指定为目录!");
            return false;
        }
        return true;
    }

    public static void deploy(File[] files) {
        if (files.length == 0) {
            //ignore
            log("没有需要上传的文件");
            System.exit(0);
        }

        boolean match = Arrays.stream(files).anyMatch(f -> f.getName().endsWith(".pom"));

        if (!match) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deploy(Objects.requireNonNull(file.listFiles()));
                }
            }
        } else {
            AtomicReference<File> pom = new AtomicReference<>();
            AtomicReference<File> jar = new AtomicReference<>();
            AtomicReference<File> source = new AtomicReference<>();
            AtomicReference<File> javadoc = new AtomicReference<>();
            Arrays.stream(files).filter(f -> f.getName().endsWith(".pom") || f.getName().endsWith(".jar")).forEach(file -> {
                if (file.isFile()) {
                    String name = file.getName();
                    if (DATE_PATTERN.matcher(name).find()) {
                        // skip 忽略日期快照版本，如 xxx-mySql-2.2.6-20170714.095105-1.jar
                    } else if (name.endsWith(".pom")) {
                        pom.set(file);
                    } else if (name.endsWith("javadoc.jar")) {
                        javadoc.set(file);
                    } else if (name.endsWith("sources.jar")) {
                        source.set(file);
                    } else if (name.endsWith(".jar")) {
                        jar.set(file);
                    }
                }
            });

            if (pom.get() != null) {
                if (jar.get() != null) {
                    deploy(pom.get(), jar.get(), source.get(), javadoc.get());
                } else {
                    deployPom(pom.get());
                }
            }
        }

    }

    @Deprecated
    public static boolean packingIsPom(File pom) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pom)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().contains("<packaging>pom</packaging>")) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void deployPom(final File pom) {
        EXECUTOR_SERVICE.execute(() -> {
            String cmd = BASE_CMD + " -DpomFile=" + pom.getName() + " -Dpackaging=pom -Dfile=" + pom.getName();
            exec(cmd, pom);
        });
    }

    public static void deploy(final File pom, final File jar, final File source, final File javadoc) {
        EXECUTOR_SERVICE.execute(() -> {
            StringBuilder cmd = new StringBuilder(BASE_CMD);
            cmd.append(" -DpomFile=").append(pom.getName());
            if (jar != null) {
                //当有bundle类型时，下面的配置可以保证上传的jar包后缀为.jar
                cmd.append(" -Dpackaging=jar -Dfile=").append(jar.getName());
            } else {
                cmd.append(" -Dfile=").append(pom.getName());
            }
            if (source != null) {
                cmd.append(" -Dsources=").append(source.getName());
            }
            if (javadoc != null) {
                cmd.append(" -Djavadoc=").append(javadoc.getName());
            }
            exec(cmd.toString(), pom);
        });
    }

    private static void exec(String cmd, File pomFile) {
        try {
            final Process proc = CMD.exec(cmd, null, pomFile.getParentFile());
            InputStream inputStream = proc.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String line;
            StringBuilder logBuffer = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[INFO]") || line.startsWith("[WARN]") || line.startsWith("[ERROR]") ||
                        line.startsWith("Upload") || line.startsWith("Download")) {
                    logBuffer.append(Thread.currentThread().getName()).append(" : ").append(line).append("\n");
                }
            }
            log(logBuffer.toString());
            int result = proc.waitFor();
            if (result != 0) {
                error("上传失败：" + pomFile.getAbsolutePath());
                error(cmd);
            }
        } catch (IOException | InterruptedException e) {
            error("上传失败：" + pomFile.getAbsolutePath());
            e.printStackTrace();
        }
    }

    private static void error(String message) {
        // region 检查文件是否存在
        File errorFile = null;
        try {
            errorFile = new File(ERROR_LOG);
            if (!errorFile.exists()) {
                boolean newFile = errorFile.createNewFile();
                if (!newFile) {
                    log("创建日志文件失败；" + ERROR_LOG);
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // endregion

        try (OutputStreamWriter streamWriter = new OutputStreamWriter(new FileOutputStream(errorFile, Boolean.TRUE), StandardCharsets.UTF_8)) {
            streamWriter.write(message);
            streamWriter.write("\r\n");
        } catch (IOException e) {
            e.printStackTrace();
            error(e.toString());
        }
    }

    private static void log(String message) {
        // region 检查文件是否存在
        File errorFile = null;
        try {
            errorFile = new File(RUNNING_LOG);
            if (!errorFile.exists()) {
                boolean newFile = errorFile.createNewFile();
                if (!newFile) {
                    System.err.println("创建日志文件失败；" + RUNNING_LOG);
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // endregion

        System.out.println(message);
        try (OutputStreamWriter streamWriter = new OutputStreamWriter(new FileOutputStream(errorFile, Boolean.TRUE), StandardCharsets.UTF_8)) {
            streamWriter.write(message);
            streamWriter.write("\r\n");
        } catch (IOException e) {
            e.printStackTrace();
            error(e.toString());
        }
    }
}
