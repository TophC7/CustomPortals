{
  description = "Custom Portals Foxified; build portals from any block for NeoForge 1.21.1";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in
    {
      devShells.${system}.default =
        let
          glfw-libs = with pkgs; [
            # OpenGL / Mesa
            libGL
            libglvnd

            # GLFW + X11 dependencies (Xwayland fallback)
            glfw
            libx11
            libxcursor
            libxrandr
            libxinerama
            libxi
            libxxf86vm
            libxext
            libxrender
            libxtst

            # Wayland
            wayland
            libxkbcommon

            # Audio (OpenAL)
            openal
            libpulseaudio
            alsa-lib

            # Misc native deps LWJGL may dlopen
            flite
            udev
          ];
        in
        pkgs.mkShell {
          packages = [
            pkgs.jdk21
            pkgs.gradle
          ];

          JAVA_HOME = "${pkgs.jdk21}";

          LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath glfw-libs;
        };

      ## nix run -- build JAR into dist/ ##
      apps.${system}.default = {
        type = "app";
        program = toString (
          pkgs.writeShellScript "build" ''
            set -euo pipefail
            export JAVA_HOME="${pkgs.jdk21}"

            ${pkgs.gradle}/bin/gradle --no-daemon jar

            mod_version="$(sed -n "s/^mod_version=//p" gradle.properties | head -n 1)"
            if [ -z "$mod_version" ]; then
              echo "Missing mod_version in gradle.properties" >&2
              exit 1
            fi

            rm -rf dist
            mkdir -p dist
            for jar in build/libs/*-"$mod_version".jar; do
              [ -e "$jar" ] || continue
              case "$jar" in
                *-sources.jar) continue ;;
              esac
              cp "$jar" dist/
            done

            if ! ls dist/*.jar >/dev/null 2>&1; then
              echo "No current jar found for version $mod_version" >&2
              exit 1
            fi

            echo "Built:"
            ls dist/*.jar
          ''
        );
      };
    };
}
