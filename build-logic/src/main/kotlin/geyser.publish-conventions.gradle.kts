plugins {
    id("geyser.shadow-conventions")
    id("net.kyori.indra.publishing")
}

indra {
    publishSnapshotsTo("geysermc", "https://nexus.bjd-mc.com:8443/repository/maven-snapshots/")
    publishReleasesTo("geysermc", "https://nexus.bjd-mc.com:8443/repository/maven-releases")
}
