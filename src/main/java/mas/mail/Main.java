package mas.mail;

import mas.mail.app.ConsoleApp;
import mas.mail.app.MailAssistant;
import mas.mail.config.AppConfig;
import mas.mail.eval.Evaluator;
import mas.mail.ui.MainWindow;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

/**
 * starting point of the program
 * <pre>
 *   (no option)            graphical interface
 *   --cli                  console interface
 *   --eval [--drafts]      run the tests on data/testset (routing, and drafts if asked)
 *   --config FILE          config file to use (default config/app.properties)
 *   --set key=value        change one config value (can be used many times)
 *   --mock                 same as --set llm.provider=mock
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Path base = Path.of("").toAbsolutePath();
        Path configFile = base.resolve("config/app.properties");
        String mode = "gui";
        boolean drafts = false;
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configFile = Path.of(args[++i]).toAbsolutePath();
            }
        }
        AppConfig config = AppConfig.load(base, configFile);
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cli" -> mode = "cli";
                case "--eval" -> mode = "eval";
                case "--drafts" -> drafts = true;
                case "--mock" -> config.set("llm.provider", "mock");
                case "--set" -> {
                    String[] kv = args[++i].split("=", 2);
                    config.set(kv[0].trim(), kv.length > 1 ? kv[1].trim() : "");
                }
                case "--config" -> i++;
                case "--help", "-h" -> {
                    System.out.println("Usage: java -jar mail-assistant.jar [--cli | --eval [--drafts]] "
                            + "[--config FILE] [--set key=value]... [--mock]");
                    return;
                }
                default -> {
                    System.err.println("Unknown option " + args[i]);
                    return;
                }
            }
        }
        if ("gui".equals(mode) && GraphicsEnvironment.isHeadless()) {
            mode = "cli";
        }
        switch (mode) {
            case "eval" -> new Evaluator(config).run(drafts);
            case "cli" -> new ConsoleApp(new MailAssistant(config)).run();
            default -> MainWindow.launch(config);
        }
    }
}
