package fr.elias.ultimateChristmas.boss;

public class GrinchSpawnData {
    public String world;
    public double x, y, z;
    public long cooldownSeconds;  // how often to spawn
    public long staySeconds;      // how long to stay spawned

    public GrinchSpawnData() {}

    public GrinchSpawnData(String world, double x, double y, double z, long cooldownSeconds, long staySeconds) {
        this.world = world; this.x = x; this.y = y; this.z = z;
        this.cooldownSeconds = cooldownSeconds; this.staySeconds = staySeconds;
    }
}
