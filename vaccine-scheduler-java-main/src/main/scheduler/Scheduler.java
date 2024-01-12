package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    public static void main(String[] args) {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>"); 
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>"); 
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>"); 
        System.out.println("> reserve <date> <vaccine>"); 
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>"); 
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");
        System.out.println("> logout"); 
        System.out.println("> quit");
        System.out.println();

        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
            }
        }
    }

    private static void createPatient(String[] tokens) {
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        if (validatePassword(password)){
            if (usernameExistsPatient(username)) {
                System.out.println("Username taken, try again!");
                return;
            }
            byte[] salt = Util.generateSalt();
            byte[] hash = Util.generateHash(password, salt);
            try {
                Patient patient = new Patient.PatientBuilder(username, salt, hash).build();
                patient.saveToDB();
                System.out.println("Created user " + username);
            } catch (SQLException e) {
                System.out.println("Failed to create user.");
                e.printStackTrace();
            }
        }
        else{
            System.out.println("- Password must be at least 8 characters long");
            System.out.println("- Must include at least one character from each of the following types:");
            System.out.println("  - Uppercase letters (A-Z)");
            System.out.println("  - Lowercase letters (a-z)");
            System.out.println("  - Numbers (0-9)");
            System.out.println("  - Special characters (e.g., !@#$%^&*()_+-=[]{}|;':\",.<>?/)");
            return;
        }
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            Caregiver caregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build(); 
            // save to caregiver information to our database
            caregiver.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        // login_patient <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (patient == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null ) {
            System.out.println("Please login first!");
            return;
        } else if (currentCaregiver != null) {
            System.out.println("welcome doctor, here is the schedule!");
        } else {
            System.out.println("welcome our patients, here is the schedule!");
        }
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String availableCaregivers = "SELECT C.Username FROM Caregivers C, Availabilities A WHERE A.Time = ? AND C.Username = A.Username GROUP BY C.Username ORDER BY C.Username ASC";
        String availableVaccine = "SELECT Name, Doses FROM Vaccines";
        try {
            PreparedStatement statement1 = con.prepareStatement(availableCaregivers);
            statement1.setString(1, tokens[1]);
            ResultSet result1 = statement1.executeQuery();
            PreparedStatement statement2 = con.prepareStatement(availableVaccine);
            ResultSet result2 = statement2.executeQuery();
            //System.out.println(result1);
            if(result1.isBeforeFirst()) {
                System.out.print("Available Caregivers: ");
                while (result1.next()) { // if there is caregiver available, show vaccines
                    System.out.print(result1.getString(1) + " ");
                }
                System.out.print("\n");
                while (result2.next()){
                    System.out.println("Vaccines: " + result2.getString("Name") + " Available Doses: " + result2.getString("Doses"));
                }
            }
            else {
                System.out.println("No available caregiver!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }

    }

    private static void reserve(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");//no one login
            return;
        } else if (currentCaregiver != null) {
            System.out.println("Please login as a patient!");//doctor login
            return;
        }
        if (tokens.length != 3) {
            System.out.println("Please try again!");//wrong input
            return;
        }
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String caregiver_reserve = "SELECT A.Username FROM Availabilities AS A WHERE A.Time = ? ORDER BY A.Username ASC";//check if there is caregiver available
        String vaccine_reserve = "SELECT Name, Doses FROM Vaccines WHERE Vaccines.Name = ?";//check if there is vaccine available
        try {
            PreparedStatement statement1 = con.prepareStatement(caregiver_reserve);
            statement1.setString(1, tokens[1]);
            ResultSet result1 = statement1.executeQuery();

            PreparedStatement statement2 = con.prepareStatement(vaccine_reserve);
            statement2.setString(1, tokens[2]);
            ResultSet result2 = statement2.executeQuery();

            Boolean existCaregiver = result1.next();
            Boolean existVaccine = result2.next();
            if (result2.getInt("Doses") <= 0) {
                existVaccine = false;
            }
            if (existCaregiver && existVaccine) {
                String reservedID = "SELECT ID FROM Appointments";//get the last ID
                PreparedStatement IdStatement = con.prepareStatement(reservedID);
                ResultSet lastID = IdStatement.executeQuery();
                int id=1;
                if (lastID.isBeforeFirst()) {
                    while (lastID.next()){
                        id = lastID.getInt("ID");
                    }
                    id = id+1;
                }
                // create a new appointment
                //Date date = Date.valueOf(tokens[1]);
                String caregiverReserved = result1.getString(1);
                String appointment = "INSERT INTO Appointments VALUES (?, ?, ?, ?, ?)";
                PreparedStatement updateStatement = con.prepareStatement(appointment);
                updateStatement.setString(1, Integer.toString(id)); // id
                updateStatement.setString(2, caregiverReserved); // caregiver
                updateStatement.setString(3, currentPatient.getUsername()); // patient
                updateStatement.setString(4, tokens[2]); // vaccine
                updateStatement.setString(5, tokens[1]); // date
                updateStatement.executeUpdate();
                // update vaccine doses value
                Vaccine vaccine = new Vaccine.VaccineGetter(tokens[2]).get();
                vaccine.decreaseAvailableDoses(1);
                // delete date availability of caregiver
                String deleteAvailability = "DELETE FROM Availabilities WHERE Username = ? AND Time = ?";
                PreparedStatement dStatement = con.prepareStatement(deleteAvailability);
                dStatement.setString(1, caregiverReserved);
                dStatement.setString(2, tokens[1]);
                dStatement.executeUpdate();
                System.out.println("Appointment ID: " + id);
                System.out.println("Caregiver username: " + caregiverReserved);
            }
            else if (!existCaregiver){//no available caregiver
                System.out.println("No available caregiver!");
            }
            else {//no available doses
                System.out.println("No available vaccine!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null ) {
            System.out.println("Please login first!");
            return;
        } else if (currentCaregiver != null) {
            System.out.println("welcome doctor, you are going to cancel an appointment!");
        } else {
            System.out.println("welcome our patients, you are going to cancel an appointment!");
        }
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        //caregiver and patient can just cancel their own appointment!
        String patientcheck = "SELECT * FROM Appointments WHERE ID = ? AND P_name = ?";
        String caregivercheck = "SELECT * FROM Appointments WHERE ID = ? AND C_name = ?";
        String cancel = "DELETE FROM Appointments WHERE Appointments.ID = ?";
        String insert = "INSERT INTO Availabilities VALUES (?, ?)";
        try {
            if(currentPatient != null ){
                PreparedStatement statementCheck = con.prepareStatement(patientcheck);
                statementCheck.setString(1, tokens[1]);
                statementCheck.setString(2, currentPatient.getUsername());
                ResultSet resultCheck = statementCheck.executeQuery();
                if (resultCheck.next()) {
                    // delete the appointment
                    PreparedStatement statementCancel = con.prepareStatement(cancel);
                    statementCancel.setString(1, tokens[1]);
                    statementCancel.executeUpdate();
                    // insert availabilities
                    PreparedStatement statementInsert = con.prepareStatement(insert);
                    statementInsert.setString(1, resultCheck.getString(5)); // Time
                    statementInsert.setString(2, resultCheck.getString(2)); // Caregiver
                    statementInsert.executeUpdate();
                    // add dose
                    Vaccine vaccine = new Vaccine.VaccineGetter(resultCheck.getString(4)).get();
                    vaccine.increaseAvailableDoses(1);
                    System.out.println("Canceled successfully!");
                }
                else {
                    System.out.println("You have no such appointment!");
                }
            }
            else{
                PreparedStatement statementCheck = con.prepareStatement(caregivercheck);
                statementCheck.setString(1, tokens[1]);
                statementCheck.setString(2, currentCaregiver.getUsername());
                ResultSet resultCheck = statementCheck.executeQuery();
                if (resultCheck.next()) {
                    // delete the appointment
                    PreparedStatement statementCancel = con.prepareStatement(cancel);
                    statementCancel.setString(1, tokens[1]);
                    statementCancel.executeUpdate();
                    // insert availabilities
                    PreparedStatement statementInsert = con.prepareStatement(insert);
                    statementInsert.setString(1, resultCheck.getString(5)); // Time
                    statementInsert.setString(2, resultCheck.getString(2)); // Caregiver
                    statementInsert.executeUpdate();
                    // add dose
                    Vaccine vaccine = new Vaccine.VaccineGetter(resultCheck.getString(4)).get();
                    vaccine.increaseAvailableDoses(1);
                    System.out.println("Canceled successfully!");
                }
                else {
                    System.out.println("You have no such appointment!");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }

    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        if (tokens.length != 1) {
            System.out.println("Please try again!");
            return;
        }
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String username = "";
        String appointmentSearch = "";
        if (currentCaregiver != null) {
            username = currentCaregiver.getUsername();
            appointmentSearch = "SELECT ID, V_name, Time, P_name FROM Appointments WHERE C_name = ? ORDER BY ID";
        }
        else if (currentPatient != null) {
            username = currentPatient.getUsername();
            appointmentSearch = "SELECT ID, V_name, Time, C_name FROM Appointments WHERE P_name = ? ORDER BY ID";
        }
        try {
            PreparedStatement statement = con.prepareStatement(appointmentSearch);
            statement.setString(1, username);
            ResultSet result = statement.executeQuery();
            //boolean existAppointment = result.next();
            if(result.isBeforeFirst()) {
                while (result.next()) {
                    System.out.println("Appointment ID: " + result.getString(1));
                    System.out.println("Vaccine: " + result.getString(2));
                    System.out.println("Date: " + result.getString(3));
                    if (currentPatient != null) {
                        System.out.println("Caregiver: " + result.getString(4));
                    } else {
                        System.out.println("Patient: " + result.getString(4));
                    }
                }
            } else {
                System.out.println("You have no appointment! Having a good day!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void logout(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first.");
            return;
        }
        if (tokens.length != 1) {
            System.out.println("Please try again!");
            return;
        }
        currentPatient = null;
        currentCaregiver = null;
        System.out.println("Successfully logged out!");
        return;
    }


    private static boolean validatePassword(String password) {
        return checkLength(password) && checkCharacterTypes(password);
    }

    private static boolean checkLength(String password) {
        return password.length() >= 8;
    }

    private static boolean checkCharacterTypes(String password) {
        String uppercaseRegex = ".*[A-Z].*";
        String lowercaseRegex = ".*[a-z].*";
        String digitRegex = ".*\\d.*";
        String specialCharRegex = ".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",.<>?/].*";

        return password.matches(uppercaseRegex) &&
                password.matches(lowercaseRegex) &&
                password.matches(digitRegex) &&
                password.matches(specialCharRegex);
    }
}
