.
├── docker-compose.yml
├── datomic
│   ├── config
│   │   └── transactor.properties
│   └── data
└── app
├── Dockerfile
├── pom.xml
└── src
└── main
└── java
└── com
└── example
└── DatomicDemo.java

# run the database
docker compose up datomic-db

# run the java application
docker compose up  datomic-java-app

# or easiest to just debug from the IntelliJ window
DatomicDemo