package org.telegram.ui.Components.CameraFilters;

import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.List;

public class CameraFilterRegistry {
    private static final List<CameraFilterModel> FILTERS = new ArrayList<>();

    static {
        // 0. Normal
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.ORIGINAL,
            R.string.CameraFilter_Original_Title,
            R.string.CameraFilter_Original_Sub,
            0xFFFFFFFF,
            0xFF888888,
            R.drawable.ic_lens_original,
            1.0f
        ));

        // 1. Glow / Beauty
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.BEAUTY,
            R.string.CameraFilter_Beauty_Title,
            R.string.CameraFilter_Beauty_Sub,
            0xFFFF80AB,
            0xFFFF4081,
            R.drawable.ic_lens_beauty,
            0.85f
        ));

        // 2. Golden Hour
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.GOLDEN_HOUR,
            R.string.CameraFilter_GoldenHour_Title,
            R.string.CameraFilter_GoldenHour_Sub,
            0xFFFFB300,
            0xFFFF6F00,
            R.drawable.ic_lens_golden_hour,
            0.9f
        ));

        // 3. 1990 Vintage
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.VINTAGE_90S,
            R.string.CameraFilter_Vintage90s_Title,
            R.string.CameraFilter_Vintage90s_Sub,
            0xFFBCAAA4,
            0xFF795548,
            R.drawable.ic_lens_vintage_90s,
            0.85f
        ));

        // 4. Cyberpunk
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.CYBERPUNK,
            R.string.CameraFilter_Cyberpunk_Title,
            R.string.CameraFilter_Cyberpunk_Sub,
            0xFF00E5FF,
            0xFFFF007F,
            R.drawable.ic_lens_cyberpunk,
            0.95f
        ));

        // 5. Noir
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.BW_NOIR,
            R.string.CameraFilter_BwNoir_Title,
            R.string.CameraFilter_BwNoir_Sub,
            0xFFE0E0E0,
            0xFF212121,
            R.drawable.ic_lens_bw_noir,
            1.0f
        ));

        // 6. Pastel Anime
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.PASTEL_ANIME,
            R.string.CameraFilter_PastelAnime_Title,
            R.string.CameraFilter_PastelAnime_Sub,
            0xFFB388FF,
            0xFF80D8FF,
            R.drawable.ic_lens_pastel_anime,
            0.85f
        ));

        // 7. VHS Glitch
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.VHS_GLITCH,
            R.string.CameraFilter_VhsGlitch_Title,
            R.string.CameraFilter_VhsGlitch_Sub,
            0xFF00E676,
            0xFFFF1744,
            R.drawable.ic_lens_vhs_glitch,
            0.8f
        ));

        // 8. Fisheye
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.FISHEYE,
            R.string.CameraFilter_Fisheye_Title,
            R.string.CameraFilter_Fisheye_Sub,
            0xFF00E5FF,
            0xFF7C4DFF,
            R.drawable.ic_lens_fisheye,
            0.85f
        ));

        // 9. Thermal
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.THERMAL,
            R.string.CameraFilter_Thermal_Title,
            R.string.CameraFilter_Thermal_Sub,
            0xFFFF1744,
            0xFF00E5FF,
            R.drawable.ic_lens_thermal,
            0.95f
        ));

        // 10. Sepia
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.SEPIA,
            R.string.CameraFilter_Sepia_Title,
            R.string.CameraFilter_Sepia_Sub,
            0xFFD7CCC8,
            0xFF8D6E63,
            R.drawable.ic_lens_sepia,
            0.9f
        ));

        // 11. Night Vision
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.NIGHT_VISION,
            R.string.CameraFilter_NightVision_Title,
            R.string.CameraFilter_NightVision_Sub,
            0xFF76FF03,
            0xFF1B5E20,
            R.drawable.ic_lens_night_vision,
            0.95f
        ));

        // 12. Comic Pop Art
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.COMIC_POPART,
            R.string.CameraFilter_ComicPopArt_Title,
            R.string.CameraFilter_ComicPopArt_Sub,
            0xFFFFD600,
            0xFFFF1744,
            R.drawable.ic_lens_comic_popart,
            0.9f
        ));

        // 13. Rose Blush
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.ROSE_BLUSH,
            R.string.CameraFilter_RoseBlush_Title,
            R.string.CameraFilter_RoseBlush_Sub,
            0xFFF48FB1,
            0xFFC2185B,
            R.drawable.ic_lens_rose_blush,
            0.85f
        ));

        // 14. Cold Ice
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.COLD_ICE,
            R.string.CameraFilter_ColdIce_Title,
            R.string.CameraFilter_ColdIce_Sub,
            0xFF80D8FF,
            0xFF0091EA,
            R.drawable.ic_lens_cold_ice,
            0.85f
        ));

        // 15. Cinema Teal
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.CINEMA_TEAL,
            R.string.CameraFilter_CinemaTeal_Title,
            R.string.CameraFilter_CinemaTeal_Sub,
            0xFF00B4D8,
            0xFFFF7B00,
            R.drawable.ic_lens_cinema_teal,
            0.9f
        ));

        // 16. Portra 400
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.PORTRA_400,
            R.string.CameraFilter_Portra400_Title,
            R.string.CameraFilter_Portra400_Sub,
            0xFFFFD166,
            0xFFF77F00,
            R.drawable.ic_lens_portra_400,
            0.85f
        ));

        // 17. Fuji Velvia
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.FUJI_VELVIA,
            R.string.CameraFilter_FujiVelvia_Title,
            R.string.CameraFilter_FujiVelvia_Sub,
            0xFF06D6A0,
            0xFF118AB2,
            R.drawable.ic_lens_fuji_velvia,
            0.9f
        ));

        // 18. Vaporwave
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.VAPORWAVE,
            R.string.CameraFilter_Vaporwave_Title,
            R.string.CameraFilter_Vaporwave_Sub,
            0xFFFF70A6,
            0xFF70D6FF,
            R.drawable.ic_lens_vaporwave,
            0.9f
        ));

        // 19. Dreamy Bloom
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.DREAMY_BLOOM,
            R.string.CameraFilter_DreamyBloom_Title,
            R.string.CameraFilter_DreamyBloom_Sub,
            0xFFFFD6E0,
            0xFFC1FBA4,
            R.drawable.ic_lens_dreamy_bloom,
            0.85f
        ));

        // 20. Sin City Red
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.SINCITY_RED,
            R.string.CameraFilter_SinCityRed_Title,
            R.string.CameraFilter_SinCityRed_Sub,
            0xFFFF0055,
            0xFF2B2D42,
            R.drawable.ic_lens_sincity_red,
            0.95f
        ));

        // 21. Emerald Forest
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.EMERALD_FOREST,
            R.string.CameraFilter_EmeraldForest_Title,
            R.string.CameraFilter_EmeraldForest_Sub,
            0xFF2EC4B6,
            0xFF011627,
            R.drawable.ic_lens_emerald_forest,
            0.9f
        ));

        // 22. Sunset Peach
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.SUNSET_PEACH,
            R.string.CameraFilter_SunsetPeach_Title,
            R.string.CameraFilter_SunsetPeach_Sub,
            0xFFFFAB91,
            0xFFFF7043,
            R.drawable.ic_lens_sunset_peach,
            0.85f
        ));

        // 23. Prism Refraction
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.PRISM_REFRACTION,
            R.string.CameraFilter_PrismRefraction_Title,
            R.string.CameraFilter_PrismRefraction_Sub,
            0xFFFF4081,
            0xFF7C4DFF,
            R.drawable.ic_lens_prism_refraction,
            0.85f
        ));

        // 24. Mocha Warm
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.MOCHA_WARM,
            R.string.CameraFilter_MochaWarm_Title,
            R.string.CameraFilter_MochaWarm_Sub,
            0xFFA1887F,
            0xFF4E342E,
            R.drawable.ic_lens_mocha_warm,
            0.9f
        ));

        // 25. Duotone Neon
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.DUOTONE_NEON,
            R.string.CameraFilter_DuotoneNeon_Title,
            R.string.CameraFilter_DuotoneNeon_Sub,
            0xFF7C4DFF,
            0xFFFFAB00,
            R.drawable.ic_lens_duotone_neon,
            0.95f
        ));

        // 26. Holographic
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.HOLOGRAPHIC,
            R.string.CameraFilter_Holographic_Title,
            R.string.CameraFilter_Holographic_Sub,
            0xFF64FFDA,
            0xFFEA80FC,
            R.drawable.ic_lens_holographic,
            0.9f
        ));

        // 27. Acid Pop
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.ACID_POP,
            R.string.CameraFilter_AcidPop_Title,
            R.string.CameraFilter_AcidPop_Sub,
            0xFFEEFF41,
            0xFFFF1744,
            R.drawable.ic_lens_acid_pop,
            0.9f
        ));

        // 28. Blade Runner
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.BLADE_RUNNER,
            R.string.CameraFilter_BladeRunner_Title,
            R.string.CameraFilter_BladeRunner_Sub,
            0xFFFF7043,
            0xFF00B0FF,
            R.drawable.ic_lens_blade_runner,
            0.9f
        ));

        // 29. Wes Anderson
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.WES_ANDERSON,
            R.string.CameraFilter_WesAnderson_Title,
            R.string.CameraFilter_WesAnderson_Sub,
            0xFFFFEE58,
            0xFFFF7043,
            R.drawable.ic_lens_wes_anderson,
            0.85f
        ));

        // 30. Kodak Gold
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.KODAK_GOLD,
            R.string.CameraFilter_KodakGold_Title,
            R.string.CameraFilter_KodakGold_Sub,
            0xFFFFC107,
            0xFFD32F2F,
            R.drawable.ic_lens_kodak_gold,
            0.9f
        ));

        // 31. Ilford HP5
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.ILFORD_HP5,
            R.string.CameraFilter_IlfordHp5_Title,
            R.string.CameraFilter_IlfordHp5_Sub,
            0xFFFAFAFA,
            0xFF111111,
            R.drawable.ic_lens_ilford_hp5,
            1.0f
        ));

        // 32. Soft Aura
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.SOFT_AURA,
            R.string.CameraFilter_SoftAura_Title,
            R.string.CameraFilter_SoftAura_Sub,
            0xFFE1BEE7,
            0xFFFFF59D,
            R.drawable.ic_lens_soft_aura,
            0.85f
        ));

        // 33. Peach Glow
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.PEACH_GLOW,
            R.string.CameraFilter_PeachGlow_Title,
            R.string.CameraFilter_PeachGlow_Sub,
            0xFFFFAB91,
            0xFFFF4081,
            R.drawable.ic_lens_peach_glow,
            0.85f
        ));

        // 34. Honey Warmth
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.HONEY_WARMTH,
            R.string.CameraFilter_HoneyWarmth_Title,
            R.string.CameraFilter_HoneyWarmth_Sub,
            0xFFFFB300,
            0xFFE65100,
            R.drawable.ic_lens_honey_warmth,
            0.9f
        ));

        // 35. Lavender Haze
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.LAVENDER_HAZE,
            R.string.CameraFilter_LavenderHaze_Title,
            R.string.CameraFilter_LavenderHaze_Sub,
            0xFFCE93D8,
            0xFF7C4DFF,
            R.drawable.ic_lens_lavender_haze,
            0.85f
        ));

        // 36. Super 8
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.SUPER_8,
            R.string.CameraFilter_Super8_Title,
            R.string.CameraFilter_Super8_Sub,
            0xFFFF8A65,
            0xFF8D6E63,
            R.drawable.ic_lens_super_8,
            0.9f
        ));

        // 37. Polaroid 600
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.POLAROID_600,
            R.string.CameraFilter_Polaroid600_Title,
            R.string.CameraFilter_Polaroid600_Sub,
            0xFF80CBC4,
            0xFFFFCC80,
            R.drawable.ic_lens_polaroid_600,
            0.85f
        ));

        // 38. Retro Disco
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.RETRO_DISCO,
            R.string.CameraFilter_RetroDisco_Title,
            R.string.CameraFilter_RetroDisco_Sub,
            0xFFFF4081,
            0xFF00E5FF,
            R.drawable.ic_lens_retro_disco,
            0.95f
        ));

        // 39. Dark Academia
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.DARK_ACADEMIA,
            R.string.CameraFilter_DarkAcademia_Title,
            R.string.CameraFilter_DarkAcademia_Sub,
            0xFFA1887F,
            0xFF3E2723,
            R.drawable.ic_lens_dark_academia,
            0.9f
        ));

        // 40. Retro Scanlines
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.RETRO_SCANLINES,
            R.string.CameraFilter_RetroScanlines_Title,
            R.string.CameraFilter_RetroScanlines_Sub,
            0xFF69F0AE,
            0xFF2979FF,
            R.drawable.ic_lens_retro_scanlines,
            0.85f
        ));

        // 41. Vortex Warp
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.VORTEX_WARP,
            R.string.CameraFilter_VortexWarp_Title,
            R.string.CameraFilter_VortexWarp_Sub,
            0xFFB388FF,
            0xFF651FFF,
            R.drawable.ic_lens_vortex_warp,
            0.85f
        ));

        // 42. Kaleidoscope
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.KALEIDOSCOPE,
            R.string.CameraFilter_Kaleidoscope_Title,
            R.string.CameraFilter_Kaleidoscope_Sub,
            0xFFFF80AB,
            0xFF8C9EFF,
            R.drawable.ic_lens_kaleidoscope,
            0.85f
        ));

        // 43. Infrared Aero
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.INFRARED_AERO,
            R.string.CameraFilter_InfraredAero_Title,
            R.string.CameraFilter_InfraredAero_Sub,
            0xFFFF1744,
            0xFFF50057,
            R.drawable.ic_lens_infrared_aero,
            0.95f
        ));

        // 44. Neon Cyan Magenta
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.NEON_CYAN_MAGENTA,
            R.string.CameraFilter_NeonCyanMagenta_Title,
            R.string.CameraFilter_NeonCyanMagenta_Sub,
            0xFF00E5FF,
            0xFFFF007F,
            R.drawable.ic_lens_neon_cyan_magenta,
            0.95f
        ));

        // 45. Gold Dust
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.GOLD_DUST,
            R.string.CameraFilter_GoldDust_Title,
            R.string.CameraFilter_GoldDust_Sub,
            0xFFFFD700,
            0xFFFF6F00,
            R.drawable.ic_lens_gold_dust,
            0.9f
        ));

        // 46. Anamorphic Flare
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.ANAMORPHIC_FLARE,
            R.string.CameraFilter_AnamorphicFlare_Title,
            R.string.CameraFilter_AnamorphicFlare_Sub,
            0xFF00E5FF,
            0xFF2979FF,
            R.drawable.ic_lens_anamorphic_flare,
            0.95f
        ));

        // 47. Edge Neon Glow
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.EDGE_NEON_GLOW,
            R.string.CameraFilter_EdgeNeonGlow_Title,
            R.string.CameraFilter_EdgeNeonGlow_Sub,
            0xFFFF007F,
            0xFF00E5FF,
            R.drawable.ic_lens_edge_neon_glow,
            0.95f
        ));

        // 48. Radioactive Glow
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.RADIOACTIVE_GLOW,
            R.string.CameraFilter_RadioactiveGlow_Title,
            R.string.CameraFilter_RadioactiveGlow_Sub,
            0xFF76FF03,
            0xFF00E676,
            R.drawable.ic_lens_radioactive_glow,
            0.95f
        ));

        // 49. Solar Eclipse
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.SOLAR_ECLIPSE,
            R.string.CameraFilter_SolarEclipse_Title,
            R.string.CameraFilter_SolarEclipse_Sub,
            0xFFFFD54F,
            0xFF212121,
            R.drawable.ic_lens_solar_eclipse,
            0.95f
        ));

        // 50. Pixel Art 8-Bit
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.PIXEL_ART_8BIT,
            R.string.CameraFilter_PixelArt8bit_Title,
            R.string.CameraFilter_PixelArt8bit_Sub,
            0xFFFF1744,
            0xFF00E5FF,
            R.drawable.ic_lens_pixel_art_8bit,
            0.9f
        ));

        // 51. Double Exposure
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.DOUBLE_EXPOSURE,
            R.string.CameraFilter_DoubleExposure_Title,
            R.string.CameraFilter_DoubleExposure_Sub,
            0xFF00E5FF,
            0xFFFF4081,
            R.drawable.ic_lens_double_exposure,
            0.85f
        ));

        // 52. Magma Volcano
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.MAGMA_VOLCANO,
            R.string.CameraFilter_MagmaVolcano_Title,
            R.string.CameraFilter_MagmaVolcano_Sub,
            0xFFFF3D00,
            0xFFFFEA00,
            R.drawable.ic_lens_magma_volcano,
            0.95f
        ));

        // 53. Deep Abyss Ocean
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.DEEP_ABYSS_OCEAN,
            R.string.CameraFilter_DeepAbyssOcean_Title,
            R.string.CameraFilter_DeepAbyssOcean_Sub,
            0xFF00E5FF,
            0xFF006064,
            R.drawable.ic_lens_deep_abyss_ocean,
            0.9f
        ));

        // 54. Glitch Datamosh
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.GLITCH_DATAMOSH,
            R.string.CameraFilter_GlitchDatamosh_Title,
            R.string.CameraFilter_GlitchDatamosh_Sub,
            0xFF00E5FF,
            0xFFFF007F,
            R.drawable.ic_lens_glitch_datamosh,
            0.9f
        ));

        // 55. Starlight Sparkle
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.STARLIGHT_SPARKLE,
            R.string.CameraFilter_StarlightSparkle_Title,
            R.string.CameraFilter_StarlightSparkle_Sub,
            0xFFFFFFFF,
            0xFFFFD700,
            R.drawable.ic_lens_starlight_sparkle,
            0.85f
        ));

        // 56. Chrono Speed Blur
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.CHRONO_SPEED_BLUR,
            R.string.CameraFilter_ChronoSpeedBlur_Title,
            R.string.CameraFilter_ChronoSpeedBlur_Sub,
            0xFF00E5FF,
            0xFF7C4DFF,
            R.drawable.ic_lens_chrono_speed_blur,
            0.85f
        ));

        // 57. Midnight Purple
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.MIDNIGHT_PURPLE,
            R.string.CameraFilter_MidnightPurple_Title,
            R.string.CameraFilter_MidnightPurple_Sub,
            0xFFE040FB,
            0xFF651FFF,
            R.drawable.ic_lens_midnight_purple,
            0.95f
        ));

        // 58. Ripple Water
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.RIPPLE_WATER_DROPS,
            R.string.CameraFilter_RippleWater_Title,
            R.string.CameraFilter_RippleWater_Sub,
            0xFF00E5FF,
            0xFF0091EA,
            R.drawable.ic_lens_ripple_water,
            0.9f
        ));

        // 59. Bulge Warp
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.PINCH_BULGE_LENS,
            R.string.CameraFilter_BulgeWarp_Title,
            R.string.CameraFilter_BulgeWarp_Sub,
            0xFFFF4081,
            0xFF7C4DFF,
            R.drawable.ic_lens_bulge_warp,
            0.9f
        ));

        // 60. Retro GameBoy
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.RETRO_GAMEBOY,
            R.string.CameraFilter_GameBoy_Title,
            R.string.CameraFilter_GameBoy_Sub,
            0xFF8BAC0F,
            0xFF306230,
            R.drawable.ic_lens_gameboy,
            0.95f
        ));

        // 61. Cyber Matrix Rain
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.CYBER_MATRIX_RAIN,
            R.string.CameraFilter_MatrixRain_Title,
            R.string.CameraFilter_MatrixRain_Sub,
            0xFF00E676,
            0xFF004D40,
            R.drawable.ic_lens_matrix_rain,
            0.9f
        ));

        // 62. Neon Wireframe
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.NEON_WIREFRAME_GRID,
            R.string.CameraFilter_NeonWireframe_Title,
            R.string.CameraFilter_NeonWireframe_Sub,
            0xFFFF007F,
            0xFF00E5FF,
            R.drawable.ic_lens_neon_wireframe,
            0.95f
        ));

        // 63. Quad Mirror
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.MIRROR_QUAD_SPLIT,
            R.string.CameraFilter_QuadMirror_Title,
            R.string.CameraFilter_QuadMirror_Sub,
            0xFFFF4081,
            0xFF00E5FF,
            R.drawable.ic_lens_quad_mirror,
            0.9f
        ));

        // 64. Tunnel Zoom
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.TUNNEL_ZOOM_WARP,
            R.string.CameraFilter_TunnelZoom_Title,
            R.string.CameraFilter_TunnelZoom_Sub,
            0xFF7C4DFF,
            0xFF00E5FF,
            R.drawable.ic_lens_tunnel_zoom,
            0.85f
        ));

        // 65. Holo Beam
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.HOLOGRAM_BLUE_GLITCH,
            R.string.CameraFilter_HoloBeam_Title,
            R.string.CameraFilter_HoloBeam_Sub,
            0xFF00E5FF,
            0xFF2979FF,
            R.drawable.ic_lens_holo_beam,
            0.9f
        ));

        // 66. Heatwave Mirage
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.HEATWAVE_MIRAGE,
            R.string.CameraFilter_HeatwaveMirage_Title,
            R.string.CameraFilter_HeatwaveMirage_Sub,
            0xFFFF6D00,
            0xFFFFD54F,
            R.drawable.ic_lens_heatwave_mirage,
            0.9f
        ));

        // 67. Ink Sketch
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.CROSS_HATCH_SKETCH,
            R.string.CameraFilter_InkSketch_Title,
            R.string.CameraFilter_InkSketch_Sub,
            0xFFE0E0E0,
            0xFF212121,
            R.drawable.ic_lens_ink_sketch,
            0.95f
        ));

        // 68. 1920 Silent Cinema
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.VINTAGE_SEPIA_FILM_SCRATCH,
            R.string.CameraFilter_SilentCinema_Title,
            R.string.CameraFilter_SilentCinema_Sub,
            0xFFD7CCC8,
            0xFF5D4037,
            R.drawable.ic_lens_silent_cinema,
            0.9f
        ));

        // 69. Crystal Sphere
        FILTERS.add(new CameraFilterModel(
            CameraFilterType.CHROMATIC_SPHERE,
            R.string.CameraFilter_CrystalSphere_Title,
            R.string.CameraFilter_CrystalSphere_Sub,
            0xFFE040FB,
            0xFF00E5FF,
            R.drawable.ic_lens_crystal_sphere,
            0.9f
        ));
    }

    public static List<CameraFilterModel> getFilters() {
        return FILTERS;
    }

    public static List<CameraFilterModel> getAllFilters() {
        return FILTERS;
    }

    public static CameraFilterModel getFilterByType(int filterType) {
        for (CameraFilterModel model : FILTERS) {
            if (model.id == filterType) {
                return model;
            }
        }
        return FILTERS.get(0);
    }

    public static int getFilterCount() {
        return FILTERS.size();
    }
}
