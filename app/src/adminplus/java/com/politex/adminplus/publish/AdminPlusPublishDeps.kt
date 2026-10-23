package com.politex.adminplus.publish

import android.content.Context
import com.politex.BuildConfig

object AdminPlusPublishDeps {
    private val defaultPublisherFactory: (Context) -> GithubPublisher = {
        GithubDataApiPublisher(
            api = OkHttpGithubApi(),
            owner = BuildConfig.GITHUB_OWNER,
            repo = BuildConfig.GITHUB_REPO,
        )
    }

    private val defaultPayloadBuilder: (Context) -> PublishPayload? = { OwnerPublishPayloadBuilder.build(it) }

    var publisherFactory: (Context) -> GithubPublisher = defaultPublisherFactory
    var payloadBuilder: (Context) -> PublishPayload? = defaultPayloadBuilder

    fun reset() {
        publisherFactory = defaultPublisherFactory
        payloadBuilder = defaultPayloadBuilder
    }
}
