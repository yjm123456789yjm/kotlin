/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import circlet.pipelines.config.dsl.api.Ide

fun warmupWithDist(ide: Ide) {
    job("Warmup ${ide.name} with dist") {
        startOn {
            // run on schedule every day at 5AM
            schedule { cron("0 5 * * *") }
        }

        warmup(ide) {
            scriptLocation = "./.devenv/warmup-dist.sh"
        }

        git {
            // fetch the entire commit history
            depth = UNLIMITED_DEPTH
            // fetch all branches
            refSpec = "refs/*:refs/*"
        }
    }
}

warmupWithDist(Ide.IJGateway)
warmupWithDist(Ide.Fleet)
