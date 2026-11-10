# 0.29.0-alpha 领域纹路与原语字幕

第二技能新增触碰行星近景与锁敌法阵。实际重新查看用户指定的 [第二季第12集](https://www.bilibili.com/video/BV19VqMBKEMS/?p=32) 15:40–16:12短段，其中15:54–15:57为伸手接触行星、紫色纹路扩散，16:06–16:10为敌人背后的多层圆环与径向折线。原片仅存于 `qa/planet-touch-reference` 供观察；交付不含这些帧或片段。特写使用 Minecraft 自带史蒂夫手臂与 UV，触点、沿球面扩散的护罩和锁敌法阵为原创代码。保留原有音频、伤害时刻与天体贴图来源。

侧面主线的上下端点直接接入各自连续的分叉网络，顶、底与侧线共享同一组顶点。隔离实机检查对实际绘制线段进行端点连通性遍历，确认主线可贯通球冠；低画质不删减这些连接。

领域重新对照第一季第20集竞技场展开的竖向细光线、错位分支、紫色光幕与角色贴身曲线。参考画面来自 [OTAKU SINH 第20集图文](https://otakusinh.com/2023/02/17/a-lasting-impression-the-eminence-in-shadow-episode-20-review/)，其中 `screenshot-02-15-2023-125017.jpg`、`125122.jpg`、`125135.jpg` 只保存在 `qa/anime-study-029` 供观察。几何与着色代码原创；参考图片不进入玩家 JAR 或交付包。

删除蓄力小球；领域不采用经纬网格。原版史蒂夫与0.28剑模型保持不变。字幕由独立的 `voice_captions.json` 描述各原声片段的语言与时间段，中文界面不再翻译配音台词。

---

0.28.0-alpha: the sword mesh, bevels and atlas are authored by tools/build_shadow_blade_028.py using the existing local OBJ helpers. Domain circuits are original straight-segment geometry; only the caster has curved aura ribbons, following the user's explicit correction. Aura color/bright-core relationships were observed in an anime screenshot linked at https://sorenamoo.com/the-eminence-in-shadow-pembahasan-lengkap-jurus-i-am-atomic-milik-cid-kagenou/ (image: https://i.postimg.cc/y6cQtkTh/4cc83-16772458920423-1920.jpg). The reference image remains QA-only and is not redistributed. Steve, skill audio and planetary sources retain their attribution below.

0.27.0-alpha: custom avatar geometry, garment/face atlas and replacement hands have been removed from the player artifact. The local player uses Minecraft's bundled wide Steve model and texture through the vanilla renderer; no Minecraft texture is redistributed. New domain opening/contraction rings and frozen-mob pause glyphs are original geometry. Existing skill audio and planetary textures retain their attribution below.

Historical avatar records (these avatar assets are not in 0.27.0-alpha):

0.26.0-alpha: original tailored cloth geometry by tools/shadow-avatar/silhouette.py. User selected silhouette changes while keeping the existing face. Reference-face texels/geometry/UV and hair unchanged. Existing garment attribution below remains applicable. New cloth diffuse tile is original, occupies atlas (320,400)-(367,487).

Current 0.25.1-alpha: garment pixels originally authored for the user's existing Shadow avatar using SuiSuiNian721's minecraft-pixel-skin skill (commit 3f589e03e0b21938db6a1c8a1b06d63438d13546).
Source: https://github.com/SuiSuiNian721/minecraft-pixel-skin
Tool license: Attribution-NonCommercial 1.0, https://github.com/SuiSuiNian721/minecraft-pixel-skin/blob/main/LICENSE ; LICENSE, NOTICE and vendor notices are retained in the release. Tool license and independently authored garment output are distinguished per NOTICE. No bundled eye/hair template or guide artwork is included in the garment output. The adapted review page excludes unrelated source guide artwork; the local capture script normalizes Classic model naming. Existing reference-face pixels are unchanged; only clothing diffuse texels and 60 hood UV faces change, no geometry or animation changes. Production tool: tools/shadow-avatar/pixel_cloth.py. Native Classic 64x64 review garment template 0.2.0 is mapped to the existing 512x512 atlas; the study's schematic head is not applied to the game.
Atlas SHA256: 1bbc085aa7e66c4b5f2f00627ada7b8def6c7e1d9ed6e66908b4a218ae45089f

Historical records:

Current 0.21.2-alpha: the selected user reference face is rotated -6.400139839 degrees with nearest-neighbour sampling to undo screenshot roll. No facial features were redrawn. The original reference crop remains unchanged in tools/shadow-avatar/baseline-atlas.png; production uses face_texture.py via build_avatar.py. Only the face region of the shipped atlas changes from 0.21.1-alpha.

Historical records:

Current 0.21.1-alpha: chest folds/fastening, continuous cuff borders and glove shading are original pixel-authored diffuse tiles made by tools/shadow-avatar/build_avatar.py; consistent texel density replaces the repeated raised ribs and floating cuff rails. The user supplied example-skin screenshot is a style reference only; no pixels or downloaded skins from that screenshot are included. Original reference-face crop from 0.21.0 is byte-identical. Solid hood/hair/collar and the other existing sources retain their attribution below.

Historical records:

Current 0.21.0-alpha: face pixels come from the user's supplied Doubao reference image 影之实力者主角形象.png (SHA256 38bbdab6069150b2bf55a1d9922ccdaa926fc684cea1e254453a891d0d0cfb52). Crop (775,160,965,325), resized to 128x128 and placed at atlas (0,384); no redrawn/sculpted facial features. The selected solid clothing, hair and closed head are authored geometry, 70 cubes plus 568 surfaces with computed normals. Production generator: tools/shadow-avatar/build_avatar.py; baseline atlas/spec are preserved beside it. No reference-mod assets or code were extracted. The player avatar is enabled only while holding the slime sword; account skin identity is retained. Existing audio and Earth sources retain their attribution below.

Historical records:

Current 0.19.0-alpha: detailed Shadow outfit visually referenced against the official season-two anime character sheet at https://shadow-garden.jp/2nd/character/ (chara1_cid__main2.png). Original authored 113-box articulated geometry and 512x512 procedural atlas are generated by tools/build_detailed_shadow_model.py with tools/shadow-suit-model-template.java. No reference pixels, official mesh or reference-mod assets were copied. Official artwork stays QA-only; source URLs and hashes are in qa/skin-study-0.19.0/reference-sources.json. The vascular field is original seeded branching code. Existing audio, Earth textures, camera correction and Recovery original voice are retained.

以下为原版本历史记录：

Current 0.12.1-alpha: original protagonist Recovery Atomic from the user-selected Bilibili BV19VqMBKEMS P23 (season 2 episode 3, cid35232877818), 16:51.45–16:54.05, 2.6 seconds at original speed. Rights belong to the original owners. The original scene mix retains music/effects; no actor voice synthesis or separated vocal stem. All three procedural Recovery cue sounds are disabled in playback per the user's latest request. Existing six audio assets, Earth attribution, shaders and recovery visuals remain unchanged. Full reference videos/frames remain QA-only. See AUDIO_SOURCES.md and dist/0.12.1-alpha/voice-source.json.

以下为原版本历史记录：

Current 0.12.0-alpha: Recovery Atomic uses original procedural violet-white rings, gold filaments and a real-framebuffer black/white contour filter. Three recovery sounds are originally synthesized by tools/generate_recovery_audio.py. The user's vampire queen battle reference was located in Bilibili BV19VqMBKEMS, season 2 episode 3 (cid35232877818); local clips/contact sheets are stored only under qa/recovery-reference. No anime frames, reference-mod code/assets or Recovery dialogue are embedded in this feature. The existing Earth attribution and original six audio sources retain their earlier provenance.

以下为原版本历史记录：

Current 0.11.2-alpha: the former ringed foreground sphere is replaced by one original procedural star with granulation, spots, limb darkening and a local corona. Remaining system bodies are uniformly reduced to 62% and placed on one cinematic line; Saturn rings are absent in the current scene. This follows the latest user art direction rather than astronomical scale or a literal copy of the anime. Earth source images and all six existing audio assets are unchanged.

# Planetary scene sources

The Shooting Star Demo is a visual reference only. No code, textures, models, shaders or audio have been extracted from its JAR.

Earth daytime texture (original 2048×1024 PNG, unmodified): NASA/Goddard Space Flight Center Scientific Visualization Studio. The Blue Marble data is courtesy of Reto Stockli (NASA/GSFC) and NASA Earth Observatory.
Source page: https://svs.gsfc.nasa.gov/2915
Asset: https://svs.gsfc.nasa.gov/vis/a000000/a002900/a002915/bluemarble-2048.png

Earth nighttime texture (original 2048×1024 PNG, unmodified): data courtesy Marc Imhoff (NASA/GSFC) and Christopher Elvidge (NOAA/NGDC). Image by Craig Mayhew (NASA/GSFC) and Robert Simmon (NASA/GSFC).
Source page: https://svs.gsfc.nasa.gov/2916
Asset: https://svs.gsfc.nasa.gov/vis/a000000/a002900/a002916/earthatnight-2048.png
NASA image/media guidelines: https://www.nasa.gov/nasa-brand-center/images-and-media/
No NASA insignia, endorsement, or logo is used. These credited source images are separate from the project's own implementation license.

Stars, cloud motion, atmosphere, lighting, protective shell, other planets, rings, framing and burst rendering are original procedural shader code. Celestial arrangement and scale are cinematic rather than an astronomical simulation.

Anime visual reference supplied by the user: Bilibili BV19VqMBKEMS P32, second season episode12. Public unauthenticated playback metadata, cid35232943869, qn16. Locally inspected short reference video segments15:00–16:00 and16:00–19:00; contact sheets and reference clips remain in qa/planetary-reference only, outside the player JAR. The 16:10–16:32 samples show enemy lock glyph, the spoken attack, Saturn foreground and receding other planets, then white/violet illumination.

Current second-skill soundtrack reuses the six existing sounds, including the full Japanese I Am Atomic voice; no claim that a new P32 voice has been cut or human synchronized. See AUDIO_SOURCES.md. No anime video frames are embedded in the game scene.

SHA256 earth_day.png: ae6214b078ed0864c96f74bcb10ae3021f6eb116f8059797efe0fa9ea8b89d35

SHA256 earth_night.png: 28407a1a802868852b3c232dbeaf16b5165e8200898cc7df6a2fec70708556cd
