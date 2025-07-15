{ pkgs ? import <nixpkgs> {} }:
(pkgs.buildFHSEnv {
  name = "electron_env";
  targetPkgs = pkgs:
            (with pkgs; [
              alsa-lib
              atkmm
              at-spi2-atk
              cairo
              cups
              dbus
              expat
              glib
              glibc
              gtk2
              gtk3
              gtk4
              libdrm
              libgbm
              libxkbcommon
              mesa
              libglvnd
              nspr
              nss
              nodePackages.pnpm
              nodejs_20
              pango
              udev
              zlib
              squashfsTools
            ])
            ++ (with pkgs.xorg; [
              libXcomposite
              libXdamage
              libXext
              libXfixes
              libXrandr
              libX11
              xcbutil
              libxcb
            ]);
  runScript = "bash";
  profile = ''
    export LD_LIBRARY_PATH=${pkgs.mesa}/lib:${pkgs.libglvnd}/lib:${pkgs.glibc}/lib  '';
}).env
