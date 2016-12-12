node{
    stage("Prepare") {
        deleteDir()
        dir("release") {
            git "https://github.com/harrisonrodgers/synchrobench.git"
        }
        sh "cp -r release debug"
    }

    def gccVers = [4, 5, 6]

    for (int i in gccVers) {
        stage("Build GCC ${i}") {
            docker.image("gcc:${i}").inside {
                sh "gcc -v"
                parallel release: {
                    retry(2) {
                        sh "make clean -C release/c-cpp"
                        sh "make -C release/c-cpp"
                    }
                }, debug: {
                    retry(2) {
                        sh "make clean -C debug/c-cpp"
                        sh "VERSION=DEBUG make -C debug/c-cpp"
                    }
                },
                failFast: false
            }
        }
    }

    stage("Test") {
        deleteDir()
    }

}
