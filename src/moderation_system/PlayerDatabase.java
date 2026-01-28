package moderation_system;

import arc.util.Log;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import java.sql.*;
import java.util.LinkedList;
import java.util.Random;

public class PlayerDatabase {
    Connection databaseConnection;
    private final Random randomGenerator = new Random();
    LinkedList<String> nonShadowBanned;
    LinkedList<String> shadowBanned;

    public PlayerDatabase() throws SQLException {
        Connection connection;
        Administration.Config databaseUsername = new Administration.Config("db-username",
                "The username for the moderation database.", "");
        Administration.Config databasePassword = new Administration.Config("db-password",
                "The password for the moderation database.", "");

        try {
            Class.forName("org.sqlite.JDBC");
            String url = String.format("jdbc:sqlite:%s", Main.databaseLocation.string());
            connection = DriverManager.getConnection(url, databaseUsername.string(), databasePassword.string());
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            return;
        }

        this.nonShadowBanned = new LinkedList<>();
        this.shadowBanned = new LinkedList<>();

        this.databaseConnection = connection;
        Statement statement = connection.createStatement();
        statement.executeUpdate("PRAGMA foreign_keys = ON;");

        statement.executeUpdate("CREATE TABLE IF NOT EXISTS players (" +
                "uuid        TEXT  PRIMARY KEY  NOT NULL, " +
                "last_ip     TEXT               NOT NULL, " +
                "last_name   TEXT               NOT NULL" +
                ");"
        );

        statement.executeUpdate("CREATE TABLE IF NOT EXISTS banned_players (" +
                "uuid        TEXT               NOT NULL, " +
                "ban_id      TEXT               NOT NULL UNIQUE, " +
                "ban_reason  TEXT               NOT NULL, " +
                "ban_start   INT                NOT NULL, " +
                "ban_end     INT                NOT NULL, " +
                "discord_id  TEXT               NOT NULL, " +
                "FOREIGN KEY (uuid)       REFERENCES players(uuid), " +
                "FOREIGN KEY (discord_id) REFERENCES staff(discord_id)" +
                ");"
        );

        statement.executeUpdate("CREATE TABLE IF NOT EXISTS staff (" +
                "discord_id  TEXT  PRIMARY KEY  NOT NULL, " +
                "uuid        TEXT               NOT NULL, " +
                "admin       INT                NOT NULL, " +
                "FOREIGN KEY (uuid) REFERENCES players(uuid)" +
                ");"
        );

        statement.executeUpdate("CREATE TABLE IF NOT EXISTS shadow_ban (" +
                "id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "uuid        TEXT               NOT NULL, " +
                "ban_reason  TEXT               NOT NULL, " +
                "ban_start   INT                NOT NULL, " +
                "ban_end     INT                NOT NULL, " +
                "FOREIGN KEY (uuid)       REFERENCES players(uuid)" +
                ");"
        );


        statement.close();
    }

    public String getUUIDFromBanID(String banID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM banned_players WHERE ban_id = ?"
        );
        statement.setString(1,banID);
        ResultSet databaseResult = statement.executeQuery();

        String uuid = databaseResult.getString("uuid");

        statement.close();

        return uuid;
    }

    public Player getPlayerFromUUID(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM players WHERE uuid = ?;"
        );
        statement.setString(1,UUID);
        ResultSet databaseResult = statement.executeQuery();

        String uuid;

        if (databaseResult.next()) {
            uuid = databaseResult.getString("uuid");
        } else {
            return null;
        }

        statement.close();
        databaseResult.close();

        return Groups.player.find(p -> p.uuid().equals(uuid));
    }

    public void addPlayer(String UUID, String lastIP, String lastName) throws SQLException {
        Player player = this.getPlayerFromUUID(UUID);
        if (player != null) {
            return;
        }
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "INSERT INTO players (uuid, last_ip, last_name) VALUES (?, ?, ?);"
        );
        statement.setString(1,UUID);
        statement.setString(2,lastIP);
        statement.setString(3,lastName);
        statement.executeUpdate();
        statement.close();
    }

    /**
     * Gets a staff member via their UUID.
     * @param UUID The UUID of the staff member.
     * @return A Player object representing the staff member.
     * @throws SQLException An exception that is thrown by SQL.
     */
    public Player getStaffFromUUID(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM staff WHERE uuid = ?;"
        );
        statement.setString(1,UUID);
        ResultSet databaseResult = statement.executeQuery();

        String uuid;

        if (databaseResult.next()) {
            uuid = databaseResult.getString("uuid");
        } else {
            return null;
        }

        statement.close();
        databaseResult.close();

        return Groups.player.find(p -> p.uuid().equals(uuid));
    }

    /**
     * Returns if a staff member has admin permissions.
     * @param staff The Player object of the staff member.
     * @return A Boolean object representing if the staff member has permissions. Returns null if the staff isn't in the
     * database.
     * @throws SQLException An exception that is thrown by SQL.
     */
    public Boolean hasAdminPermission(Player staff) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM staff WHERE uuid = ?;"
        );
        statement.setString(1,staff.uuid());
        ResultSet databaseResult = statement.executeQuery();

        boolean hasAdminPermission;

        if (databaseResult.next()) {
            hasAdminPermission = databaseResult.getInt("admin") == 1;
        } else {
            return null;
        }

        statement.close();
        databaseResult.close();

        return hasAdminPermission;
    }

    /**
     * Gets a staff member via their Discord ID.
     * @param discordID The Discord ID of the staff member.
     * @return A String representing the UUID of the staff member.
     * @throws SQLException An exception that is thrown by SQL.
     */
    @SuppressWarnings("unused")
    public String getStaffFromDiscordID(String discordID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM staff WHERE discord_id = ?;"
        );
        statement.setString(1,discordID);
        ResultSet databaseResult = statement.executeQuery();

        String uuid;

        if (databaseResult.next()) {
            uuid = databaseResult.getString("uuid");
        } else {
            return null;
        }

        statement.close();
        databaseResult.close();

        return uuid;
    }

    /**
     * Gets a staff member's Discord ID via their UUID.
     * @param UUID The UUID of the staff member.
     * @return A String representing the staff member's Discord ID.
     * @throws SQLException An exception that is thrown by SQL.
     */
    public String getStaffID(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM staff WHERE uuid = ?;"
        );
        statement.setString(1,UUID);
        ResultSet databaseResult = statement.executeQuery();

        String discordID;

        if (databaseResult.next()) {
            discordID = databaseResult.getString("discord_id");
        } else {
            return null;
        }

        statement.close();
        databaseResult.close();

        return discordID;
    }

    /**
     * Adds a staff member into the database.
     * @param UUID The UUID of the staff member.
     * @throws SQLException An exception that is thrown by SQL.
     */
    public void addStaff(String UUID, Boolean adminLevel, String discordID) throws SQLException {
        Player player = this.getStaffFromUUID(UUID);
        if (player != null) {
            Log.info("That staff member is already in the database.");
            return;
        }

        int adminLevelInteger = adminLevel ? 1 : 0;

        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "INSERT INTO staff (uuid, admin, discord_id) VALUES (?, ?, ?);"
        );
        statement.setString(1,UUID);
        statement.setInt(2, adminLevelInteger);
        statement.setString(3,discordID);
        statement.executeUpdate();
        statement.close();
    }

    /**
     * @param UUID The UUID of the staff member.
     * @throws SQLException An exception that is thrown by SQL.
     */
    public void removeStaff(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "DELETE FROM staff WHERE uuid = ?"
        );
        statement.setString(1,UUID);
        statement.execute();
        statement.close();
    }

    /**
     * Gets all staff from the database.
     * @return A ResultSet containing all of the staff.
     * @throws SQLException An exception that is thrown by SQL.
     */
    public ResultSet getAllStaff() throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement("""
                SELECT *
                FROM staff;
                """);

        return statement.executeQuery();
    }


    /**
     * Gets the start of a ban by UUID.
     * @param UUID The UUID of the target player.
     * @return A long representing the start of a player's ban.
     * @throws SQLException An exception thrown by SQL.
     */
    public long getBanStartTime(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM banned_players WHERE uuid = ?;"
        );
        statement.setString(1,UUID);
        ResultSet databaseResult = statement.executeQuery();

        long banStartTime = 0;

        if (databaseResult.next()) {
            banStartTime = databaseResult.getLong("ban_start");
        }

        statement.close();

        return banStartTime;
    }

    /**
     * Gets the start of a ban by UUID.
     * @param UUID The UUID of the target player.
     * @return A long representing the end of a player's ban.
     * @throws SQLException An exception thrown by SQL.
     */
    public long getBanEndTime(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM banned_players WHERE uuid = ?;"
        );
        statement.setString(1,UUID);
        ResultSet databaseResult = statement.executeQuery();

        long banEndTime = 0;

        if (databaseResult.next()) {
            banEndTime = databaseResult.getLong("ban_end");
        }

        statement.close();
        databaseResult.close();

        return banEndTime;
    }

    public String getBanReason(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM banned_players WHERE uuid = ?;"
        );
        statement.setString(1,UUID);
        ResultSet databaseResult = statement.executeQuery();

        String banReason = databaseResult.getString("ban_reason");

        statement.close();

        return banReason;
    }

    public String getBanID(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM banned_players WHERE uuid = ?;"
        );
        statement.setString(1,UUID);
        ResultSet databaseResult = statement.executeQuery();

        String banEndTime = databaseResult.getString("ban_id");

        statement.close();

        return banEndTime;
    }

    /**
     * @param UUID The UUID of the player to ban.
     * @param banReason The reason for banning the player.
     * @param endTime When the player's ban ends.
     * @param staffID The Discord ID of the staff member that sent the ban.
     * @throws SQLException An exception that is thrown by SQL.
     */
    public void addBan(String UUID, String banReason, String endTime, String staffID) throws SQLException {
        long currentTime = System.currentTimeMillis();
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "INSERT INTO banned_players (uuid, ban_id, ban_reason, ban_start, ban_end, discord_id) VALUES (?, ?, " +
                        "?, ?, ?, ?);"
        );

        boolean generateNewBanID = true;
        String hexBanID = "";

        while (generateNewBanID) {
            long newBanID = randomGenerator.nextLong() + (1L << 61);
            hexBanID = Long.toHexString(newBanID);

//            ResultSet databaseResult = statement.executeQuery(String.format("""
//                SELECT * FROM banned_players WHERE ban_id == '%s';
//                """, hexBanID
//            ));
//            databaseResult.close();
            generateNewBanID = false;
        }

        statement.setString(1,UUID);
        statement.setString(2,hexBanID);
        statement.setString(3,banReason);
        statement.setLong(4,currentTime);
        statement.setLong(5, Long.parseLong(endTime));
        statement.setString(6,staffID);
        statement.executeUpdate();
        statement.close();
    }

    /**
     * Checks if a ban is still active.
     * @param UUID The UUID of the player.
     * @return true if the ban is still active, false if it isn't active.
     * @throws SQLException SQLException.
     */
    public boolean checkBan(String UUID) throws SQLException {
        long currentTime = System.currentTimeMillis();
        long banEndTime = this.getBanEndTime(UUID);
        boolean banIsValid = currentTime <= banEndTime;

        if (!banIsValid) {
            this.removeBan(UUID);
        }

        return banIsValid;
    }

    public void removeBan(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "DELETE FROM banned_players WHERE UUID==?;"
        );
        statement.setString(1,UUID);
        statement.executeUpdate();
        statement.close();
    }

    /**
     * Transfers a ban from one staff member to another.
     * @param oldID The ID of the staff member that currently has the bans.
     * @param newID The ID of the staff member to gain responsibility of the bans.
     * @throws SQLException An exception thrown by SQL.
     */
    public void transferBan(String oldID, String newID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement("""
                        UPDATE banned_players
                        SET discord_id=?
                        WHERE discord_id=?;
                        """);

        statement.setString(1, newID);
        statement.setString(2, oldID);

        statement.executeUpdate();
        statement.close();
    }

    /**
     * Gets the bans from a given staff ID.
     * @param staffID The Discord ID (or the ID used in the database) of the staff.
     * @return A SQL ResultSet containing the bans.
     * @throws SQLException An exception thrown from SQL.
     */
    public ResultSet getBans(String staffID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                        """
                       SELECT *
                       FROM banned_players
                       WHERE discord_id=?;
                       """
        );

        statement.setString(1, staffID);
        // I don't close the statement because SQLite doesn't like that for some reason.
        // (fuck you sqlite)

        return statement.executeQuery();
    }

    // not yet necessary?
//    public ResultSet getShadowBans(String staffID) throws SQLException {
//        PreparedStatement statement = this.databaseConnection.prepareStatement(
//                """
//               SELECT *
//               FROM banned_players
//               WHERE discord_id=?;
//               """
//        );
//
//        statement.setString(1, staffID);
//        // I don't close the statement because SQLite doesn't like that for some reason.
//        // (fuck you sqlite)
//
//        return statement.executeQuery();
//    }

    public long getShadowBanEnd(String UUID) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM shadow_ban WHERE uuid = ?;"
        );
        statement.setString(1,UUID);
        ResultSet databaseResult = statement.executeQuery();

        long banEndTime = 0;

        if (databaseResult.next()) {
            banEndTime = databaseResult.getLong("ban_end");
        }
        statement.close();
        databaseResult.close();

        return banEndTime;
    }

    public boolean checkShadowBan(String uuid) throws SQLException {
        if (this.nonShadowBanned.contains(uuid)) {
            return false;
        } else if (this.shadowBanned.contains(uuid)) {
            return true;
        }

        long currentTime = System.currentTimeMillis();
        long banEndTime = this.getShadowBanEnd(uuid);
        boolean banIsValid = currentTime <= banEndTime;

        // not banned
        if (banEndTime == 0) {
            this.nonShadowBanned.add(uuid);
            return false;
        }

        if (!banIsValid) {
            this.removeShadowBan(uuid);
        } else {
            this.shadowBanned.add(uuid);
        }

        return banIsValid;
    }

    public void addShadowBan(String uuid, String banReason, String endTime) throws SQLException {
        this.nonShadowBanned.remove(uuid);

        long currentTime = System.currentTimeMillis();
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "INSERT INTO shadow_ban (uuid, ban_reason, ban_start, ban_end) VALUES (?, ?, " +
                        "?, ?);"
        );

        statement.setString(1, uuid);
        statement.setString(2, banReason);
        statement.setLong(3, currentTime);
        statement.setLong(4, Long.parseLong(endTime));
        statement.executeUpdate();
        statement.close();
    }

    public void removeShadowBanFromID(String id) throws SQLException {
        String uuid = this.lookupShadowBanUuid(id);
        this.shadowBanned.remove(uuid);

        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "DELETE FROM shadow_ban WHERE id==?;"
        );
        statement.setString(1, id);
        statement.executeUpdate();
        statement.close();
    }

    public void removeShadowBan(String uuid) throws SQLException {
        this.shadowBanned.remove(uuid);

        // since SQLite will delete multiple rows at once, we'll lookup the ID and then use that instead of UUID.
        // ID is unique, and the method will only return one ID. That way, we're not deleting rows en masse.
        String banId = this.lookupShadowBanId(uuid);

        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "DELETE FROM shadow_ban WHERE id==?;"
        );
        statement.setString(1, banId);
        statement.executeUpdate();
        statement.close();
    }

    public String lookupShadowBanId(String uuid) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM shadow_ban WHERE uuid = ?;"
        );
        statement.setString(1, uuid);
        ResultSet databaseResult = statement.executeQuery();

        String id = null;

        if (databaseResult.next()) {
            id = databaseResult.getString("id");
        }

        return id;
    }

    public String lookupShadowBanUuid(String id) throws SQLException {
        PreparedStatement statement = this.databaseConnection.prepareStatement(
                "SELECT * FROM shadow_ban WHERE id = ?;"
        );
        statement.setString(1, id);
        ResultSet databaseResult = statement.executeQuery();

        String uuid = null;

        if (databaseResult.next()) {
            uuid = databaseResult.getString("uuid");
        }

        return uuid;
    }
}