# Third party notices

## Better Clouds (Qendolin), MPL 2.0

The cloud renderer was first ported from Better Clouds 1.7.1 (branch 1.21.0) by Qendolin,
https://github.com/Qendolin/better-clouds, licensed under the Mozilla Public License 2.0. The cloud system has since
been rewritten from scratch (own cube field generator, per band wind, own G-buffer and composite). Only these two small helpers remain from the port; each carries the MPL header and a "Based on Better
Clouds by Qendolin" note:

- `src/main/java/dev/sborka/incognita/weather/clouds/gl/Shader.java` (shader program loader, also used by the
  precipitation renderer)
- `src/main/java/dev/sborka/incognita/weather/clouds/gl/GlSupport.java` (OpenGL capability check)

These files stay under MPL 2.0 (https://mozilla.org/MPL/2.0/); changes were made (Yarn to Mojang mappings,
NeoForge, own code paths). The rest of the mod is LGPL-3.0-or-later; MPL 2.0 section 3.3 allows such a Larger Work,
as long as the MPL files remain available under the MPL. The idea of an offscreen coverage pass followed by one
full screen composite also comes from Better Clouds; the current implementation of it is new code.
