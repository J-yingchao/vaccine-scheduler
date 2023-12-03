package scheduler.model;

import scheduler.db.ConnectionManager;
import scheduler.util.Util;

import java.sql.*;
import java.util.Arrays;

public class Patient {
    private final String Username;
    private final byte[] salt;
    private final byte[] hash;

    public Patient(PatientBuilder builder){
        this.Username = builder.Username;
        this.salt = builder.salt;
        this.hash = builder.hash;
    }

    private Patient(PatientGetter getter) {
        this.Username = getter.Username;
        this.salt = getter.salt;
        this.hash = getter.hash;
    }

    public String getUsername() { return Username; }

    public byte[] getSalt() { return salt; }

    public byte[] getHash() { return hash; }

    public void saveToDB() throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String addPatient = "INSERT INTO Patients VALUES (?, ?, ?)";
        try {
            PreparedStatement statement = con.prepareStatement(addPatient);
            statement.setString(1, this.Username);
            statement.setBytes(2, this.salt);
            statement.setBytes(3, this.hash);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    public static class PatientBuilder {
        private final String Username;
        private final byte[] salt;
        private final byte[] hash;

        public PatientBuilder(String Username, byte[] salt, byte[] hash) {
            this.Username = Username;
            this.salt = salt;
            this.hash = hash;
        }

        public Patient build() {
            return new Patient(this);
        }
    }

    public static class PatientGetter {
        private final String Username;
        private final String password;
        private byte[] salt;
        private byte[] hash;

        public PatientGetter(String Username, String password) {
            this.Username = Username;
            this.password = password;
        }

        public Patient get() throws SQLException {
            ConnectionManager cm = new ConnectionManager();
            Connection con = cm.createConnection();

            String getPatient = "SELECT Salt, Hash FROM Patients WHERE Username = ?";
            try {
                PreparedStatement statement = con.prepareStatement(getPatient);
                statement.setString(1, this.Username);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    byte[] salt = resultSet.getBytes("Salt");
                    byte[] hash = Util.trim(resultSet.getBytes("Hash"));
                    byte[] calculatedHash = Util.generateHash(password, salt);
                    if (!Arrays.equals(hash, calculatedHash)) {
                        return null;
                    } else {
                        this.salt = salt;
                        this.hash = hash;
                        return new Patient(this);
                    }
                }
                return null;
            } catch (SQLException e) {
                throw new SQLException();
            } finally {
                cm.closeConnection();
            }
        }
    }
}