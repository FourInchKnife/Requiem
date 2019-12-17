/*
 * Requiem
 * Copyright (C) 2019 Ladysnake
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; If not, see <https://www.gnu.org/licenses>.
 */
package ladysnake.requiem.api.v1.remnant;

import nerdhub.cardinal.components.api.component.Component;
import net.minecraft.entity.damage.DamageSource;

public interface DeathSuspender extends Component {
    void suspendDeath(DamageSource deathCause);

    boolean isLifeTransient();

    void setLifeTransient(boolean lifeTransient);

    void resumeDeath();

}
