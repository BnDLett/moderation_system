package moderation_system;

import arc.util.Log;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import java.sql.*;
import java.util.Random;

public class PlayerDatabase {
    Connection databaseConnection;
    private final Random randomGenerator = new Random();

    public PlayerDatabase() throws SQLException {
        Connection connection;

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", Main.databaseLocation.string()));
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            return;
        }

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
                "ban_id      TEXT               NOT NULL, " +
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


        statement.close();
    }

    public String getUUIDFromBanID(String banID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("""
                SELECT * FROM banned_players WHERE ban_id == '%s';
                """, banID
        ));

        String uuid = databaseResult.getString("uuid");

        statement.close();

        return uuid;
    }

    public Player getPlayerFromUUID(String UUID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("SELECT * FROM players WHERE uuid = '%s';", UUID
        ));

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
        Statement statement = this.databaseConnection.createStatement();

        Player player = this.getPlayerFromUUID(UUID);
        if (player != null) {
            return;
        }

        statement.executeUpdate(String.format("INSERT INTO players (uuid, last_ip, last_name) VALUES ('%s', '%s', '%s');",
                UUID, lastIP, lastName
        ));
        statement.close();
    }

    /**
     * Gets a staff member via their UUID.
     * @param UUID The UUID of the staff member.
     * @return A Player object representing the staff member.
     * @throws SQLException An exception that is thrown by SQL.
     */
    public Player getStaffFromUUID(String UUID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("SELECT * FROM staff WHERE uuid = '%s';", UUID
        ));

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
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("SELECT * FROM staff WHERE uuid = '%s';", staff.uuid()
        ));

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
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("SELECT * FROM staff WHERE discord_id = '%s';", discordID
        ));

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
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("SELECT * FROM staff WHERE uuid = '%s';", UUID
        ));

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
     * @param admin_level A boolean representing if a staff member is an admin (has permission to ban).
     * @throws SQLException An exception that is thrown by SQL.
     */
    public void addStaff(String UUID, Boolean admin_level, String discordID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();

        Player player = this.getStaffFromUUID(UUID);
        if (player != null) {
            Log.info("That staff member is already in the database.");
            return;
        }

        statement.executeUpdate(String.format("INSERT INTO staff (uuid, admin, discord_id) VALUES ('%s', %s, '%s');",
                UUID, admin_level.toString(), discordID
        ));
        statement.close();
    }

    /**
     * @param UUID The UUID of the staff member.
     * @throws SQLException An exception that is thrown by SQL.
     */
    public void removeStaff(String UUID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();
        statement.execute(String.format("DELETE FROM staff WHERE uuid='%s'", UUID));
    }


    /**
     * Gets the start of a ban by UUID.
     * @param UUID The UUID of the player whose ban you want to get.
     * @return A long representing the ban ID of a player.
     * @throws SQLException An exception thrown by SQL.
     */
    @SuppressWarnings("unused")
    public long getBanStartTime(String UUID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("""
                SELECT * FROM banned_players WHERE uuid == '%s';
                """, UUID
        ));

        long banStartTime = 0;

        if (databaseResult.next()) {
            banStartTime = databaseResult.getLong("ban_end");
        }

        statement.close();

        return banStartTime;
    }

    public long getBanEndTime(String UUID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("""
                SELECT * FROM banned_players WHERE uuid == '%s';
                """, UUID
        ));

        long banEndTime = 0;

        if (databaseResult.next()) {
            banEndTime = databaseResult.getLong("ban_end");
        }

        statement.close();
        databaseResult.close();

        return banEndTime;
    }

    public String getBanReason(String UUID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("""
                SELECT * FROM banned_players WHERE uuid == '%s';
                """, UUID
        ));

        String banReason = databaseResult.getString("ban_reason");

        statement.close();

        return banReason;
    }

    public String getBanID(String UUID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();
        ResultSet databaseResult = statement.executeQuery(String.format("""
                SELECT * FROM banned_players WHERE uuid == '%s';
                """, UUID
        ));

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
        Statement statement = this.databaseConnection.createStatement();

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

        System.out.println(hexBanID);

        statement.executeUpdate(String.format("""
                INSERT INTO banned_players (uuid, ban_id, ban_reason, ban_start, ban_end, discord_id) VALUES ('%s', '%s',
                 '%s', '%s', '%s', '%s');
               \s""", UUID, hexBanID, banReason, currentTime, endTime, staffID
        ));
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
            Statement statement = this.databaseConnection.createStatement();

            statement.executeUpdate(String.format("""
                DELETE FROM banned_players WHERE UUID=='%s';
                """, UUID
            ));

            statement.close();
        }

        return banIsValid;
    }

    public void removeBan(String UUID) throws SQLException {
        Statement statement = this.databaseConnection.createStatement();
        statement.execute(String.format("DELETE FROM banned_players WHERE uuid='%s'", UUID));
    }
}
