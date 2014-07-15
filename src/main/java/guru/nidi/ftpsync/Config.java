package guru.nidi.ftpsync;

import org.apache.commons.cli.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class Config {
    private static final Pattern REMOTE = Pattern.compile("(.*?)@(.*?):(.*?)");
    private String host;
    private String username;
    private String password;
    private String identity;
    private boolean secure;
    private String localDir;
    private String remoteDir;

    public Config(String[] args) {
        final Options options = createOptions();
        try {
            CommandLineParser parser = new BasicParser();
            CommandLine cmd = parser.parse(options, args);
            password = cmd.getOptionValue('p');
            identity = cmd.getOptionValue('i');
            if (password == null && identity == null) {
                throw new IllegalArgumentException("Either password or identity must be given");
            }
            secure = cmd.getOptionValue('s') != null || identity != null;
            final List<String> argList = cmd.getArgList();
            if (argList.size() != 2) {
                throw new IllegalArgumentException("Source and destination directory needed");
            }
            localDir = argList.get(0);
            String rawRemote = argList.get(1);
            final Matcher matcher = REMOTE.matcher(rawRemote);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Remote must have format <user>@<host>:<path>");
            }
            username = matcher.group(1);
            host = matcher.group(2);
            remoteDir = matcher.group(3);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar ftpsync.jar <options> <sourceDir> <destDir>", options);
            System.exit(1);
        }
    }

    private static Options createOptions() {
        final Options options = new Options();
        options.addOption(OptionBuilder.withDescription("The password").isRequired(false).withArgName("password").hasArg(true).create('p'));
        options.addOption(OptionBuilder.withDescription("The private key").isRequired(false).withArgName("private key").hasArg(true).create('i'));
        options.addOption(OptionBuilder.withDescription("If SFTP should be used").isRequired(false).withArgName("sftp").hasArg(false).create('s'));
        return options;
    }

    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getIdentity() {
        return identity;
    }

    public boolean isSecure() {
        return secure;
    }

    public String getLocalDir() {
        return localDir;
    }

    public String getRemoteDir() {
        return remoteDir;
    }
}
