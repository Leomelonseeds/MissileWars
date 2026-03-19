package com.leomelonseeds.missilewars.utilities;

import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Thanks to https://github.com/HydrolienF/VoidWorldGenerator
 */
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) { return List.of(); }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {}
    
    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {}
    
    @Override
    public void generateBedrock(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {}
    
    @Override
    public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {}

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) { return new VoidBiomeProvider(); }

    @Override
    public boolean canSpawn(World world, int x, int z) { return true; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0, 0, 0);
    }

    private class VoidBiomeProvider extends BiomeProvider {
    
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) { return Biome.THE_VOID; }
    
        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) { return List.of(Biome.THE_VOID); }
    
    }
}
