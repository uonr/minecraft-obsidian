{
  description = "Development shell for the Minecraft Obsidian NeoForge mod";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        jdk = pkgs.jdk21_headless;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            pkgs.git
          ];

          JAVA_HOME = jdk;

          shellHook = ''
            export PATH="$JAVA_HOME/bin:$PATH"
            echo "Java: $(java -version 2>&1 | head -n 1)"
            echo "Use ./gradlew compileJava or ./gradlew runClient"
          '';
        };
      });
}
