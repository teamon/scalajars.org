set :user, 'teamon'
set :domain, 'scalajars.org'
set :deploy_to, '/home/teamon/web/scalajars.org'
# set :domain, 'localhost'
# set :deploy_to, '/tmp/home/teamon/web/scalajars.org'
set :shared_paths, ["tmp", "logs"]
set :term_mode, :pretty

set :play_app_group_id, "org.scalajars"
set :play_app_artifact_id, "scalajars-web"
set :play_app_version, "0.1.0-SNAPSHOT"
set :play_app_args, "-Dhttp.port=6020 -Dupload.dir=\\/home\\/teamon\\/tmp\\/scalajars"

desc "Deploys the current version to the server."
task :deploy do
  deploy do
    # invoke :'play:publish'
    invoke :'play:create_build_file'
    invoke :'play:remove_ivy_cache'
    invoke :'play:stage'
    invoke :'play:modify_start_script'
    invoke :'deploy:link_shared_paths'

    to :launch do
      invoke :'restart'
    end
  end
end

task :setup do
  invoke :'play:install_launcher'
  invoke :'play:create_shared_dirs'
end

task :restart do
  invoke :stop
  invoke :start
end

task :start do
  queue echo_cmd(%[/home/teamon/bin/scalajars-web start])
end

task :stop do
  queue echo_cmd(%[/home/teamon/bin/scalajars-web stop])
end

namespace :play do
  desc "Install sbt launcher"
  task :install_launcher do
    queue %[
      echo "-----> Setting up sbt" && (
      if [ -x ~/bin/sbt -a -f ~/bin/sbt ]
      then
        echo "-----> sbt already set up";
      else
        #{echo_cmd(%[mkdir -p ~/bin])} &&
        #{echo_cmd(%[cd ~/bin])} &&
        echo "-----> Downloading sbt-launcher.jar" &&
        #{echo_cmd(%[curl -O -# http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.11.3-2/sbt-launch.jar 2>&1])} &&
        #{echo_cmd(%[echo 'java -Xmx512M -jar `dirname $0`/sbt-launch.jar "$@"' > sbt])} &&
        #{echo_cmd(%[chmod u+x sbt])}
      fi
      )
    ]
  end

  desc "Remove sbt launcher"
  task :remove_launcher do
    queue %[
      echo "-----> Removing sbt launcher" && (
        rm -f ~/bin/sbt ~/bin/sbt-launch.jar
      )
    ]
  end

  task :remove_ivy_cache do
    queue %[
      echo "-----> Removing ivy cache" && (
        #{echo_cmd(%[rm -rf /home/teamon/.ivy2/cache/#{play_app_group_id}])}
      )
    ]
  end

  desc "Create build.sbt file"
  task :create_build_file do
    plugins_template = <<-TEMPLATE
// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.0.3")
    TEMPLATE

    build_template = <<-TEMPLATE
seq(PlayProject.defaultSettings:_*)

resolvers += "scalajars" at "http://scalajars.org/repository"

libraryDependencies += "#{play_app_group_id}" %% "#{play_app_artifact_id}" % "#{play_app_version}" changing()
    TEMPLATE

    queue %[
      echo "-----> Creating plugins.sbt file" && (
        #{echo_cmd(%[mkdir -p project])} &&
        #{echo_cmd(%[echo '#{plugins_template}' > project/plugins.sbt])}
      )
      echo "-----> Creating build.sbt file" && (
        #{echo_cmd(%[echo '#{build_template}' > build.sbt])}
      )

    ]
  end

  task :modify_start_script do
    queue %[
      echo "-----> Modifying start script" && (
        #{echo_cmd(%[sed -i 's/$@/#{play_app_args}/' target/start])}
      )
    ]
  end

  task :create_shared_dirs do
    shared_paths.each do |dir|
      queue %[
        echo "-----> Creating '#{dir}' dir" && (
          #{echo_cmd(%[mkdir -p shared/#{dir}])}
        )
      ]
    end
  end

  task :stage do
    queue %[
      echo "-----> Running 'sbt stage'" && (
        #{echo_cmd(%[~/bin/sbt stage])}
      )
    ]
  end

  task :publish do
    system %[sbt publish]
  end
end
