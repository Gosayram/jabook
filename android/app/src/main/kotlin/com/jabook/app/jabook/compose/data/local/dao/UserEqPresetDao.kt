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

package com.jabook.app.jabook.compose.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.jabook.app.jabook.compose.data.local.entity.UserEqPresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

@Dao
public interface UserEqPresetDao {
    @Query("SELECT * FROM user_eq_presets ORDER BY created_at DESC")
    public fun getAllInternal(): Flow<List<UserEqPresetEntity>>

    public fun getAll(): Flow<List<UserEqPresetEntity>> = getAllInternal().distinctUntilChanged()

    @Insert
    public suspend fun insert(preset: UserEqPresetEntity)

    @Delete
    public suspend fun delete(preset: UserEqPresetEntity)
}
