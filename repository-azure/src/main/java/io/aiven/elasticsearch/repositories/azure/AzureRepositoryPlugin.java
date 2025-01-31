/*
 * Copyright 2020 Aiven Oy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.aiven.elasticsearch.repositories.azure;

import java.util.List;

import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;

import io.aiven.elasticsearch.repositories.AbstractRepositoryPlugin;

import com.azure.storage.blob.BlobServiceClient;

public class AzureRepositoryPlugin extends AbstractRepositoryPlugin<BlobServiceClient, AzureClientSettings> {

    public static final String REPOSITORY_TYPE = "aiven-azure";

    public AzureRepositoryPlugin(final Settings settings) {
        super(REPOSITORY_TYPE, settings, new AzureSettingsProvider());
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                AzureClientSettings.PUBLIC_KEY_FILE,
                AzureClientSettings.PRIVATE_KEY_FILE,
                AzureClientSettings.AZURE_ACCOUNT,
                AzureClientSettings.AZURE_ACCOUNT_KEY,
                AzureClientSettings.MAX_RETRIES
        );
    }

}
