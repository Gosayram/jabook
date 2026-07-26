// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.UserEqPresetDao
import com.jabook.app.jabook.compose.data.local.entity.UserEqPresetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class UserEqPresetRepository
    @Inject
    constructor(
        private val userEqPresetDao: UserEqPresetDao,
    ) {
        public fun getAllPresets(): Flow<List<UserEqPresetEntity>> = userEqPresetDao.getAll()

        public suspend fun savePreset(
            name: String,
            bands: List<Int>,
            preampMillibels: Int,
        ) {
            userEqPresetDao.insert(
                UserEqPresetEntity(
                    name = name,
                    bands = bands.joinToString(","),
                    preampMillibels = preampMillibels,
                ),
            )
        }

        public suspend fun deletePreset(entity: UserEqPresetEntity) {
            userEqPresetDao.delete(entity)
        }
    }
