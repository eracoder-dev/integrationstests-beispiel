# Stufe 1: Anwendung bauen. Maven läuft im Container, auf dem Rechner ist kein Java nötig.
FROM maven:3.9-eclipse-temurin-21 AS bau
WORKDIR /bau
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package
RUN curl -fsSL -o /postgresql.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar

# Stufe 2: WildFly mit MicroProfile-Profil, dazu Treiber, Batch, JSON-Logging und die Anwendung.
FROM quay.io/wildfly/wildfly:39.0.0.Final-jdk21
COPY --from=bau --chown=jboss:root /postgresql.jar /tmp/postgresql.jar
COPY --chown=jboss:root wildfly/konfigurieren.cli /tmp/konfigurieren.cli
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --command="module add --name=org.postgresql --resources=/tmp/postgresql.jar --dependencies=javax.api,jakarta.transaction.api" \
 && /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/konfigurieren.cli \
 && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history /tmp/*.cli /tmp/*.jar
COPY --from=bau --chown=jboss:root /bau/target/antragsbearbeitung.war /opt/jboss/wildfly/standalone/deployments/ROOT.war
