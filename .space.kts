

job("Warmup for IJGateway with `dist`") {
    // optional
    startOn {
        // run on schedule every day at 5AM
        schedule { cron("0 5 * * *") }
        // run on every commit...
        gitPush {
            // ...but only to the main branch
            branchFilter {
                +"refs/heads/main"
            }
        }
    }

    warmup(ide = Ide.IJGateway) {
        // path to the warm-up script
        scriptLocation = "./dev-env-warmup.sh"
    }

    // optional
    git {
        // fetch the entire commit history
        depth = UNLIMITED_DEPTH
        // fetch all branches
        refSpec = "refs/*:refs/*"
    }
}

job("Warmup for Fleet with `dist`") {
    // optional
    startOn {
        // run on schedule every day at 5AM
        schedule { cron("0 5 * * *") }
        // run on every commit...
        gitPush {
            // ...but only to the main branch
            branchFilter {
                +"refs/heads/main"
            }
        }
    }

    warmup(ide = Ide.Fleet) {
        // path to the warm-up script
        scriptLocation = "./dev-env-warmup.sh"
    }

    // optional
    git {
        // fetch the entire commit history
        depth = UNLIMITED_DEPTH
        // fetch all branches
        refSpec = "refs/*:refs/*"
    }
}
