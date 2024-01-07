ThisBuild / scalaVersion     := "3.3.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.pragmaxim"
ThisBuild / organizationName := "pragmaxim"

lazy val root = (project in file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "scalapass-cli",
    libraryDependencies ++= Seq(
      "dev.zio"           %% "zio"                      % "2.0.21",
      "dev.zio"           %% "zio-cli"                  % "0.5.0",
      "org.slf4j"         % "slf4j-nop"                 % "2.0.9",
      "org.eclipse.jgit"  % "org.eclipse.jgit"          % "6.8.0.202311291450-r",
      "org.eclipse.jgit"  % "org.eclipse.jgit.gpg.bc"   % "6.8.0.202311291450-r",
      "org.pgpainless"    % "pgpainless-core"           % "1.6.5",
      "org.pgpainless"    % "pgpainless-sop"            % "1.6.5",
      "org.bouncycastle"  % "bcprov-jdk18on"            % "1.77",
      "org.bouncycastle"  % "bcpg-jdk18on"              % "1.77",
      "dev.zio"           %% "zio-mock"                 % "1.0.0-RC12" % Test,
      "dev.zio"           %% "zio-test"                 % "2.0.21" % Test,
      "dev.zio"           %% "zio-test-sbt"             % "2.0.21" % Test,
      "dev.zio"           %% "zio-test-magnolia"        % "2.0.21" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
