package com.example;

import datomic.Connection;
import datomic.Database;
import datomic.Peer;
import datomic.Util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class DatomicDemo {
    private static final String DATOMIC_URI = System.getenv().getOrDefault("DATOMIC_URI", "datomic:free://localhost:4334/demo");

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Datomic Demo Application");
        System.out.println("Connecting to Datomic at: " + DATOMIC_URI);

        // Wait for Datomic to be fully available
        waitForDatomic();

        // Create database if it doesn't exist
        Peer.createDatabase(DATOMIC_URI);

        // Connect to the database
        Connection conn = Peer.connect(DATOMIC_URI);
        System.out.println("Connected to Datomic!");

        // Set up schema
        setupSchema(conn);

        // Add sample data
        addSampleData(conn);

        // Query the database
        performQueries(conn);

        // Demonstrate time travel query
        demonstrateTimeTravel(conn);

        System.out.println("Datomic demo completed successfully");
    }

    private static void waitForDatomic() {
        System.out.println("Waiting for Datomic to be available...");
        boolean connected = false;
        int attempts = 0;

        while (!connected && attempts < 30) {
            try {
                Peer.createDatabase(DATOMIC_URI);
                connected = true;
                System.out.println("Datomic is available!");
            } catch (Exception e) {
                attempts++;
                System.out.println("Waiting for Datomic to start... (attempt " + attempts + ")");
                try {
                    Thread.sleep(2000);  // Wait 2 seconds between attempts
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (!connected) {
            throw new RuntimeException("Failed to connect to Datomic after multiple attempts");
        }
    }

    private static void setupSchema(Connection conn) throws ExecutionException, InterruptedException {
        System.out.println("Setting up schema...");

        // Define the schema for a Person entity
        List<Map<String, Object>> schema = Util.list(
                // Person name
                Util.map(":db/ident", ":person/name",
                        ":db/valueType", ":db.type/string",
                        ":db/cardinality", ":db.cardinality/one",
                        ":db/doc", "A person's name"),

                // Person email
                Util.map(":db/ident", ":person/email",
                        ":db/valueType", ":db.type/string",
                        ":db/cardinality", ":db.cardinality/one",
                        ":db/unique", ":db.unique/identity",
                        ":db/doc", "A person's email address"),

                // Person age
                Util.map(":db/ident", ":person/age",
                        ":db/valueType", ":db.type/long",
                        ":db/cardinality", ":db.cardinality/one",
                        ":db/doc", "A person's age"),

                // Person friends (references to other people)
                Util.map(":db/ident", ":person/friends",
                        ":db/valueType", ":db.type/ref",
                        ":db/cardinality", ":db.cardinality/many",
                        ":db/doc", "A person's friends")
        );

        // Transact the schema
        conn.transact(schema).get();
        System.out.println("Schema setup complete");
    }

    private static void addSampleData(Connection conn) throws ExecutionException, InterruptedException {
        System.out.println("Adding sample data...");

        // First batch of data - two people
        List<Map<String, Object>> people = Util.list(
                Util.map(":person/name", "Alice Smith",
                        ":person/email", "alice@example.com",
                        ":person/age", 30),

                Util.map(":person/name", "Bob Johnson",
                        ":person/email", "bob@example.com",
                        ":person/age", 35)
        );

        Map<?, ?> results = conn.transact(people).get();

        // Get the tempids to establish relationships
        Collection<List<Object>> aliceResults = Peer.query(
                "[:find ?e :in $ ?email :where [?e :person/email ?email]]",
                conn.db(),
                "alice@example.com");

        if (aliceResults.isEmpty()) {
            throw new RuntimeException("Could not find Alice in the database");
        }
        Long aliceId = (Long) aliceResults.iterator().next().get(0);

        Collection<List<Object>> bobResults = Peer.query(
                "[:find ?e :in $ ?email :where [?e :person/email ?email]]",
                conn.db(),
                "bob@example.com");

        if (bobResults.isEmpty()) {
            throw new RuntimeException("Could not find Bob in the database");
        }
        Long bobId = (Long) bobResults.iterator().next().get(0);

        // Second batch - add a third person with friends
        List<Map<String, Object>> morePeople = Util.list(
                Util.map(":person/name", "Charlie Davis",
                        ":person/email", "charlie@example.com",
                        ":person/age", 28,
                        ":person/friends", Util.list(aliceId, bobId))
        );

        conn.transact(morePeople).get();

        // Update Alice's age
        List<Map<String, Object>> update = Util.list(
                Util.map(":db/id", aliceId,
                        ":person/age", 31)
        );

        conn.transact(update).get();

        System.out.println("Sample data added");
    }

    private static void performQueries(Connection conn) {
        System.out.println("\n--- Running Queries ---");
        Database db = conn.db();

        // Query 1: Find all people
        System.out.println("\nAll people:");
        Collection<List<Object>> allPeople = Peer.query(
                "[:find ?e ?name ?email ?age " +
                        " :where " +
                        " [?e :person/name ?name] " +
                        " [?e :person/email ?email] " +
                        " [?e :person/age ?age]]",
                db);

        for (List<Object> person : allPeople) {
            System.out.println("Entity ID: " + person.get(0) +
                    ", Name: " + person.get(1) +
                    ", Email: " + person.get(2) +
                    ", Age: " + person.get(3));
        }

        // Query 2: Find people over 30
        System.out.println("\nPeople over 30:");
        Collection<List<Object>> olderPeople = Peer.query(
                "[:find ?name ?age " +
                        " :where " +
                        " [?e :person/name ?name] " +
                        " [?e :person/age ?age] " +
                        " [(> ?age 30)]]",
                db);

        for (List<Object> person : olderPeople) {
            System.out.println("Name: " + person.get(0) + ", Age: " + person.get(1));
        }

        // Query 3: Find Charlie's friends
        System.out.println("\nCharlie's friends:");
        Collection<List<Object>> charliesFriends = Peer.query(
                "[:find ?friendName " +
                        " :where " +
                        " [?e :person/name \"Charlie Davis\"] " +
                        " [?e :person/friends ?friend] " +
                        " [?friend :person/name ?friendName]]",
                db);

        for (List<Object> friend : charliesFriends) {
            System.out.println("Friend: " + friend.get(0));
        }
    }

    private static void demonstrateTimeTravel(Connection conn) {
        System.out.println("\n--- Time Travel Query ---");

        // Get the current database value (latest state)
        Database dbNow = conn.db();

        // Find Alice's current age
        Collection<List<Object>> currentAge = Peer.query(
                "[:find ?age " +
                        " :where " +
                        " [?e :person/name \"Alice Smith\"] " +
                        " [?e :person/age ?age]]",
                dbNow);

        Long aliceCurrentAge = null;
        if (!currentAge.isEmpty()) {
            aliceCurrentAge = (Long) currentAge.iterator().next().get(0);
        }
        System.out.println("Alice's current age: " + (aliceCurrentAge != null ? aliceCurrentAge : "not found"));

        // Get all transactions in the database
        Collection<List<Object>> txs = Peer.query(
                "[:find ?tx ?when " +
                        " :where " +
                        " [?tx :db/txInstant ?when]]",
                dbNow);

        // Get a database value from before Alice's age was updated
        // This assumes the second transaction was when we added the initial people
        List<Object> secondTx = txs.stream()
                .sorted((tx1, tx2) -> ((java.util.Date)tx1.get(1)).compareTo((java.util.Date)tx2.get(1)))
                .skip(1)
                .findFirst()
                .orElse(null);

        if (secondTx != null) {
            Long txId = (Long) secondTx.get(0);
            Database dbPast = conn.db().asOf(txId);

            Collection<List<Object>> pastAge = Peer.query(
                    "[:find ?age " +
                            " :where " +
                            " [?e :person/name \"Alice Smith\"] " +
                            " [?e :person/age ?age]]",
                    dbPast);

            Long alicePastAge = null;
            if (!pastAge.isEmpty()) {
                alicePastAge = (Long) pastAge.iterator().next().get(0);
            }
            System.out.println("Alice's age in the past: " + (alicePastAge != null ? alicePastAge : "not found"));

            if (alicePastAge != null && aliceCurrentAge != null) {
                System.out.println("Demonstrated time travel - Alice's age changed from " +
                        alicePastAge + " to " + aliceCurrentAge);
            } else {
                System.out.println("Could not demonstrate time travel - missing data");
            }
        }
    }
}