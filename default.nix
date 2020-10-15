{ pkgs ? import <nixpkgs> {}, writeShellScriptBin ? pkgs.writeShellScriptBin, maven ? pkgs.maven }:
writeShellScriptBin "mvn2nix" ''
     MAVEN_OPTS=-Dorg.slf4j.simpleLogger.logFile=System.err ${maven}/bin/mvn -Dmaven.repo.local=$(mktemp -d -t mavenXXX) org.nixos.mvn2nix:mvn2nix-maven-plugin:mvn2nix "$@"
''