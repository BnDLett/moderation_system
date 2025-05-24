package moderation_system;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import mindustry.net.Administration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import static java.lang.Integer.parseInt;
import static mindustry.Vars.netServer;

public class Main extends Plugin {
    public final String pluginMessageName = "[gray]<[#003ec8]Moderation[gray]>[white] ";
    private final Map<String, String> playerIdentifiers = new HashMap<>();
    private final Random randomGenerator = new Random();
    private boolean databaseConfigured;
    private PlayerDatabase database;
    public static Administration.Config databaseLocation;
    public static Administration.Config reportFormURL;

    /**
     * @param adminLevel whether to only allow up to admin level.
     * @return A boolean representing the player's permission to execute the command.
     */
    public Boolean checkPermission(Boolean adminLevel, String UUID) throws SQLException {
        Player player = database.getPlayerFromUUID(UUID);
        Boolean hasDatabaseAdmin = database.hasAdminPermission(player);

        if (hasDatabaseAdmin == null) {
            return false;
        }

//        Log.info(hasDatabaseAdmin);
        return hasDatabaseAdmin || !adminLevel;
    }

    // https://stackoverflow.com/a/2904266
    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void databaseNotConfiguredWarning() {
        databaseConfigured = false;
        Log.warn("There is no configuration for the database! Ensure you've set it up so that players" +
                " can join.");
    }

    private void setupDatabase() {
        try {
            database = new PlayerDatabase();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(){
        databaseLocation = new Administration.Config("db-location",
                "The location of the MDN Moderation database.", "");

        reportFormURL = new Administration.Config("report-form-url", "The URL for the report form.",
                "");

        if (databaseLocation.string().isEmpty()) {
            databaseNotConfiguredWarning();
        }

        // Looking at this in 3/6/2025, I genuinely do not know what I was doing with this.
        // The code I wrote on 8/25/24 clearly was NOT the best.
        if (!databaseConfigured) {
            databaseConfigured = true;
            setupDatabase();
        }

        Events.on(EventType.PlayerJoin.class, event -> {
            long newPlayerID = randomGenerator.nextInt() + (1L << 31);
            String hexPlayerID = Long.toHexString(newPlayerID);

//            event.player.sendMessage(pluginMessageName + hexPlayerID);
            playerIdentifiers.put(event.player.uuid(), hexPlayerID);

            try {
                database.addPlayer(event.player.uuid(), event.player.ip(), event.player.name());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        Events.on(EventType.PlayerLeave.class, event -> playerIdentifiers.remove(event.player.uuid()));

        Events.on(EventType.PlayerConnect.class, event -> {
            boolean banned;

            if (databaseLocation.string().isEmpty()) {
                databaseNotConfiguredWarning();
                event.player.kick("The moderation system was not properly configured. In order to join the" +
                        " server, the moderation system must first be configured. Please contact a server administrator" +
                        " to address this issue.", 0);
                return;
            }

            try {
                banned = database.checkBan(event.player.uuid());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            if (banned) {
                String banReason;
                String banID;
                long banEnd;
                Date banEndDate;

                try {
                    banReason = database.getBanReason(event.player.uuid());
                    banID = database.getBanID(event.player.uuid());
                    banEnd = database.getBanEndTime(event.player.uuid());
                    banEndDate = Date.from(Instant.ofEpochMilli(banEnd));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                String banMessage = String.format("""
                        [scarlet]You are banned from this server.
                        
                        [orange]Reason[gray]:[white] %s
                        [orange]Ban ends[gray]:[white] %s
                        
                        [orange]Ban ID[gray]:[white] %s""", banReason, banEndDate, banID
                );
                event.player.kick(banMessage, 0);
            }
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            try {
                if (checkPermission(true, event.player.uuid())) {
                    event.player.name(String.format("[#c6633e]([scarlet]Admin[#c6633e])[] %s", event.player.name()));
                }
                else if (checkPermission(false, event.player.uuid())) {
                    event.player.name(String.format("[#c6633e]([scarlet]Staff[#c6633e])[] %s", event.player.name()));
                }
            } catch (SQLException e) {
                Log.err(e);
            }
        });
    }

    private void banCommand(Player player, String reason, Long endTime, Administration.PlayerInfo playerToBan) {
        try {
            if (!checkPermission(true, player.uuid())) {
                player.sendMessage(pluginMessageName + "You do not have permission to run this command.");
                return;
            }
        } catch (SQLException e) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            return;
        }

        String staffID;
        try {
            staffID = database.getStaffID(player.uuid());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        try {
            database.addBan(playerToBan.id, reason, String.valueOf(endTime), staffID);
            String banID = database.getBanID(playerToBan.id);

            String banMessage = String.format("""
                        [scarlet]You are banned from this server.
                        [orange]Reason[gray]:[white] %s
                        
                        [orange]Ban ID[gray]:[white] %s""", reason, banID
            );

            player.sendMessage(String.format("%s The player has been banned. Ban ID: [gold]%s[]", pluginMessageName,
                    banID));

            Player playerToKick = Groups.player.find(p -> p.uuid().equals(playerToBan.id));

            if (playerToKick == null) {
                Log.warn("Not kicking " + playerToBan.id + " -- could not find player in the server.");
                return;
            }

            playerToKick.kick(banMessage, 0);
        } catch (SQLException e) {
            player.sendMessage(pluginMessageName + "[scarlet]There was an error in processing your request.");
        }
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("kick", "<id> <reason...>", "Kick a player.", (args, player) -> {
            String id = args[0];
            String reason = args[1];
            String playerUUID = getKeyByValue(playerIdentifiers, id);

            try {
                if (!checkPermission(false, player.uuid())) {
                    player.sendMessage(pluginMessageName + "You do not have permission to run this command.");
                    return;
                }
            } catch (SQLException e) {
                Log.err( e.getClass().getName() + ": " + e.getMessage() );
                return;
            }

            Player playerToKick = Groups.player.find(p -> p.uuid().equals(playerUUID));

            if(playerToKick == null){
                player.sendMessage(pluginMessageName + "[scarlet]Could not find a player by that ID.");
                return;
            }

            playerToKick.kick(reason);
            player.sendMessage(pluginMessageName + "The player has been [scarlet]kicked[].");
        });

        handler.<Player>register("warn", "<id> <reason...>", "Warn a player.", (args, player) -> {
            String id = args[0];
            String reason = args[1];
            String playerUUID = getKeyByValue(playerIdentifiers, id);

            try {
                if (!checkPermission(false, player.uuid())) {
                    player.sendMessage(pluginMessageName + "You do not have permission to run this command.");
                    return;
                }
            } catch (SQLException e) {
                Log.err( e.getClass().getName() + ": " + e.getMessage() );
                return;
            }

            Player playerToWarn = Groups.player.find(p -> p.uuid().equals(playerUUID));

            if(playerToWarn == null){
                player.sendMessage(pluginMessageName + "[scarlet]Could not find a player by that ID.");
                return;
            }

            playerToWarn.sendMessage(pluginMessageName + "[scarlet]WARNING[gray]:[white] " + reason);
            // I feel like the orange color will make it ominous and, therefore, hilarious.
            player.sendMessage(pluginMessageName + "The specified player has been [orange]warned[].");
        });

        handler.<Player>register("info", "<username...>", "Get the info of a player.", (args, player) -> {
            String username = args[0];

            try {
                if (!checkPermission(false, player.uuid())) {
                    player.sendMessage(pluginMessageName + "You do not have permission to run this command.");
                    return;
                }
            } catch (SQLException e) {
                Log.err(e.getClass().getName() + ": " + e.getMessage());
                return;
            }

            Player targetPlayer = Groups.player.find(p -> p.plainName().equalsIgnoreCase(username));

            if (targetPlayer == null) {
                player.sendMessage(pluginMessageName + "[scarlet]Could not find a player by that username.");
                return;
            }

            String targetPlayerID = playerIdentifiers.get(targetPlayer.uuid());

            String infoMessage = String.format("""
                    ID: %s
                    IP: %s
                    Last Name Used: %s[white]
                    Is admin: %b
                    """,
                    targetPlayerID,
                    targetPlayer.ip(),
                    targetPlayer.name(),
                    targetPlayer.admin()
            );
            player.sendMessage(infoMessage);
        });

        handler.<Player>register("ban", "<id> <duration> <reason...>", "Ban a player.",
                (args, player) -> {
            String id = args[0];
            long duration = parseInt(args[1]);
            String reason = args[2];

            // days * hours_in_day * minutes_in_hour * seconds_in_minute * millis_in_seconds
            long durationMillis = duration * (24 * 60 * 60) * 1000;

            long currentTime = System.currentTimeMillis();
            long endTime = currentTime + durationMillis;

            String playerUUID = getKeyByValue(playerIdentifiers, id);
            Administration.PlayerInfo playerToBan = netServer.admins.getInfo(playerUUID);

            if (playerToBan == null) {
                player.sendMessage(pluginMessageName + "[scarlet]Could not find a player by that ID.");
                return;
            }

            banCommand(player, reason, endTime, playerToBan);
        });

        handler.<Player>register("ban-by-uuid", "<uuid> <days> <reason...>", "Bans a player with " +
                "their UUID. Keep in mind that this will NOT work if the player has never joined the server before.",
                (args, player) -> {
            String playerUUID = args[0];
            long duration = parseInt(args[1]);
            String reason = args[2];

            // days * hours_in_day * minutes_in_hour * seconds_in_minute * millis_in_seconds
            long durationMillis = duration * (24 * 60 * 60) * 1000;

            long currentTime = System.currentTimeMillis();
            long endTime = currentTime + durationMillis;

            Administration.PlayerInfo playerToBan = netServer.admins.getInfo(playerUUID);

            banCommand(player, reason, endTime, playerToBan);
        });

        handler.<Player>register("unban", "<id>", "Unbans a player.", (args, player) -> {
            String id = args[0];

            try {
                if (!checkPermission(true, player.uuid())) {
                    player.sendMessage(pluginMessageName + "You do not have permission to run this command.");
                    return;
                }
            } catch (SQLException e) {
                System.err.println( e.getClass().getName() + ": " + e.getMessage() );
                return;
            }

            try {
                String playerToUnban = database.getUUIDFromBanID(id);
                database.removeBan(playerToUnban);
                player.sendMessage(pluginMessageName + "Player was successfully unbanned.");
            } catch (SQLException e) {
                player.sendMessage(pluginMessageName + "An error occurred when trying to process your request.");
                Log.err(e.getClass().getName() + ": " + e.getMessage());
            }
        });

        handler.<Player>register("hide-staff-tag", "Hides your staff/admin tag. If you run this when your" +
                "staff/admin tag is hidden, then it will show the staff/admin tag again.", (args, player) -> {
            try {
                if (!checkPermission(false, player.uuid())) {
                    player.sendMessage(pluginMessageName + "You do not have permission to run this command.");
                    return;
                }
            } catch (SQLException e) {
                System.err.println( e.getClass().getName() + ": " + e.getMessage() );
                return;
            }

            if (player.plainName().startsWith("(Admin)") || player.plainName().startsWith("(Staff)")) {
                // [#c6633e]([scarlet]Admin[#c6633e])[] Lett
                // ------------------------------------ 0

                // this codebase lowkey kind of sucks, so I'm not going to try more than I need to.
                if (player.name().contains("Admin")) {
                    player.name(player.name().split("\\[#c6633e]\\(\\[scarlet]Admin\\[#c6633e]\\)\\[] ")[1]);
                    return;
                }

                player.name(player.name().split("\\[#c6633e]\\(\\[scarlet]Staff\\[#c6633e]\\)\\[] ")[1]);
                return;
            }

            try {
                if (checkPermission(true, player.uuid())) {
                    player.name(String.format("[#c6633e]([scarlet]Admin[#c6633e])[] %s", player.name()));
                } else {
                    player.name(String.format("[#c6633e]([scarlet]Staff[#c6633e])[] %s", player.name()));
                }
            } catch (SQLException e) {
                System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            }
        });

        handler.register("report", "Provides a URL to a tally form that allows you to report a player.",
                (String[] args, Player player) -> {
            if (reportFormURL.string().isEmpty()) {
                Log.warn("Couldn't run report command. No URL provided.");
                player.sendMessage("[scarlet]Couldn't run the report command. No URL is configured.");
                return;
            }

            Call.openURI(player.con(), String.format("%s?report_id=%s", reportFormURL.string(), player.uuid()));
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("add-staff", "<uuid> <discord_id> <admin>", "Adds a new staff member. Set" +
                " admin to true if they have the ability to ban and unban.", args -> {
            String UUID = args[0];
            String discordID = args[1];
            boolean admin = args[2].equals("true") || args[2].equals("1");

            try {
                database.addStaff(UUID, admin, discordID);
                if (admin) {
                    Player player = Groups.player.find(p -> p.uuid().equals(UUID));
                    player.admin(true);
                }
            } catch (SQLException e) {
                Log.err("An error occurred when trying to process your request.");
                Log.err(e.getClass().getName() + ": " + e.getMessage());
                return;
            }

            Log.info("Staff added.");
        });

        handler.register("remove-staff", "<uuid>", "Removes a staff member.", args -> {
            String UUID = args[0];

            try {
                database.removeStaff(UUID);
            } catch (SQLException e) {
                Log.err("An error occurred when trying to process your request.");
                Log.err(e.getClass().getName() + ": " + e.getMessage());
                return;
            }

            Log.info("Staff removed.");
        });

        handler.register("transfer-bans", "<initial-id> <new-id>", "Transfers the bans from an initial" +
                "ID to a new ID.", args -> {
            String initialID = args[0];
            String newID = args[1];

            try {
                database.transferBan(initialID, newID);
            } catch (SQLException e) {
                Log.err("An error occurred when trying to process your request.");
                Log.err(e.getClass().getName() + ": " + e.getMessage());
                return;
            }

            Log.info("Ban transfer successful.");
        });

        handler.register("get-staff-bans", "<staff-discord-id>", "Gets the bans of a staff member " +
                "based on their Discord ID.", args -> {
            String discordID = args[0];
            ResultSet results;

            try {
                results = database.getBans(discordID);
            } catch (SQLException e) {
                Log.err("An error occurred when trying to process your request.");
                Log.err(e.getClass().getName() + ": " + e.getMessage());
                return;
            }

            try {
                while (results.next()) {
                    long banStart = results.getLong("ban_start");
                    long banEnd = results.getLong("ban_end");
                    Date banStartDate = Date.from(Instant.ofEpochMilli(banStart));
                    Date banEndDate = Date.from(Instant.ofEpochMilli(banEnd));

                    System.out.printf(
                            """
                                    ========================================
                                    %s
                                    - WHEN:     %s
                                    - UNTIL:    %s
                                    - WHY:      %s
                                    - WHO:      %s
                                    - STAFF ID: %s
                                    ========================================
                                    
                                    """,
                            results.getString("ban_id"),
                            banStartDate,
                            banEndDate,
                            results.getString("ban_reason"),
                            results.getString("uuid"),
                            results.getString("discord_id")
                    );
                }
            } catch (SQLException e) {
                Log.err("An error occurred when trying to process your request.");
                Log.err(e.getClass().getName() + ": " + e.getMessage());
            }
        });

        handler.register("get-all-staff", "Gets all staff from the database.", args -> {
            try {
                ResultSet results = database.getAllStaff();

                while (results.next()) {
                    String uuid = results.getString("uuid");
                    Administration.PlayerInfo player = netServer.admins.getInfoOptional(uuid);

                    System.out.printf("""
                            ========================================
                            %s
                            - UUID:                  %s
                            - Admin:                 %b
                            - Previous known name:   %s
                            - Colored previous name: %s
                            ========================================
                            
                            """,
                            results.getString("discord_id"),
                            results.getString("uuid"),
                            results.getBoolean("admin"),
                            player.plainLastName(),
                            player.lastName
                            );
                }
            } catch (SQLException e) {
                Log.err("An error occurred when trying to process your request.");
                Log.err(e.getClass().getName() + ": " + e.getMessage());
            }
        });
    }
}