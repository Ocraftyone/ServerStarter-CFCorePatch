package atm.bloodworkxgaming.serverstarter;

import atm.bloodworkxgaming.serverstarter.config.ConfigFile;
import atm.bloodworkxgaming.serverstarter.config.LockFile;
import atm.bloodworkxgaming.serverstarter.logger.PrimitiveLogger;
import atm.bloodworkxgaming.serverstarter.packtype.IPackType;
import atm.bloodworkxgaming.serverstarter.packtype.TypeFactory;
import org.apache.commons.io.FileUtils;
import org.fusesource.jansi.AnsiConsole;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;

import static org.fusesource.jansi.Ansi.ansi;


public class ServerStarter {
    public static final PrimitiveLogger LOGGER = new PrimitiveLogger(new File("serverstarter.log"));
    public static LockFile lockFile = null;
    private static Representer rep;
    private static DumperOptions options;

    static {
        rep = new Representer();
        options = new DumperOptions();
        rep.addClassTag(ConfigFile.class, Tag.MAP);
        rep.addClassTag(LockFile.class, Tag.MAP);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
    }

    public static void main(String[] args) {
        try {
            ConfigFile config = readConfig().normalize();
            lockFile = readLockFile();
            AnsiConsole.systemInstall();

            boolean installOnly = args.length > 0 && args[0].equals("install");

            LOGGER.info("ConfigFile: " + config, true);
            LOGGER.info("LockFile: " + lockFile, true);

            if (config == null || lockFile == null) {
                LOGGER.error("One file is null: config: " + config + " lock: " + lockFile);
                return;
            }

            LOGGER.info(ansi().fgRed().a("::::::::::::::::::::::::::::::::::::::::::::::::::::"));
            LOGGER.info(ansi().fgBrightBlue().a("   Minecraft-Forge Server install/launcher jar"));
            LOGGER.info(ansi().fgBrightBlue().a("   (Created by the ").fgGreen().a("\"All The Mods\"").fgBrightBlue().a(" modpack team)"));
            LOGGER.info(ansi().fgRed().a("::::::::::::::::::::::::::::::::::::::::::::::::::::"));
            LOGGER.info("");
            LOGGER.info("   This jar will launch a Minecraft Forge Modded server");
            LOGGER.info("");
            LOGGER.info(ansi().a("   Github:    ").fgBrightBlue().a("https://github.com/AllTheMods/ServerStarter"));
            LOGGER.info(ansi().a("   Discord:   ").fgBrightBlue().a("http://discord.allthepacks.com"));
            LOGGER.info("");
            LOGGER.info(ansi().a("You are playing ").fgGreen().a(config.modpack.name));
            LOGGER.info("Starting to install/launch the server, lean back!");
            LOGGER.info(ansi().fgRed().a("::::::::::::::::::::::::::::::::::::::::::::::::::::"));
            LOGGER.info("");


            if (!InternetChecker.checkConnection() && config.launch.checkOffline) {
                LOGGER.error("Problems with the Internet connection, shutting down.");
                return;
            }

            ForgeManager forgeManager = new ForgeManager(config);


            if (lockFile.checkShouldInstall(config) || installOnly) {
                IPackType packtype = TypeFactory.createPackType(config.install.modpackFormat, config);
                if (packtype == null) {
                    LOGGER.error("Unknown pack format given in config");
                    return;
                }

                packtype.installPack();
                lockFile.packInstalled = true;
                lockFile.packUrl = config.install.modpackUrl;
                saveLockFile(lockFile);


                if (config.install.installForge) {
                    String forgeVersion = packtype.getForgeVersion();
                    String mcVersion = packtype.getMCVersion();
                    forgeManager.installForge(config.install.baseInstallPath, forgeVersion, mcVersion);
                }

                if (config.launch.spongefix) {
                    lockFile.spongeBootstrapper = forgeManager.installSpongeBootstrapper(config.install.baseInstallPath);
                    saveLockFile(lockFile);
                }


                FileManager filemanger = new FileManager(config);
                filemanger.installAdditionalFiles();
                filemanger.installLocalFiles();


            } else {
                LOGGER.info("Server is already installed to correct version, to force install delete the serverstarter.lock File.");
            }

            if (installOnly) {
                LOGGER.info("Install only mod, exiting now.");
                return;
            }

            forgeManager.handleServer();
        } catch (Throwable e) {
            LOGGER.error("Some uncaught error happened.", e);
        }
    }


    /**
     * Reads the config and parses the config
     *
     * @return the configfile object
     */
    public static ConfigFile readConfig() {
        Yaml yaml = new Yaml(new Constructor(ConfigFile.class), rep, options);
        try (InputStream input = new FileInputStream(new File("server-setup-config.yaml"))) {
            return yaml.load(input);
        } catch (FileNotFoundException e) {
            LOGGER.error("There is no config file given.", e);
            throw new RuntimeException("No config file given.", e);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("Could not read config file.", e);
            throw new RuntimeException("Failed to read config file", e);
        }
    }

    /**
     * Reads the lockfile if present, returns a new if not
     */
    public static LockFile readLockFile() {
        Yaml yaml = new Yaml(new Constructor(LockFile.class), rep, options);
        File file = new File("serverstarter.lock");

        if (file.exists()) {
            try (InputStream stream = new FileInputStream(file)) {
                return yaml.load(stream);
            } catch (FileNotFoundException e) {
                return new LockFile();
            } catch (IOException e) {
                LOGGER.error("Error while reading Lock file", e);
                throw new RuntimeException("Error while reading Lock file", e);
            }
        } else {
            return new LockFile();
        }
    }

    /**
     * Writes the lockfile to disk
     *
     * @param lockFile lockfile to write
     */
    public static void saveLockFile(LockFile lockFile) {
        Yaml yaml = new Yaml(new Constructor(LockFile.class), rep, options);
        File file = new File("serverstarter.lock");
        ServerStarter.lockFile = lockFile;

        String lock = "#Auto genereated file, DO NOT EDIT!\n" + yaml.dump(lockFile);
        try {
            FileUtils.write(file, lock, "utf-8", false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}