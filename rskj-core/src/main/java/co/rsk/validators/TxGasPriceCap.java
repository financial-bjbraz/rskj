/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.validators;

import co.rsk.core.Coin;
import co.rsk.remasc.RemascTransaction;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Transaction;

import java.math.BigInteger;

/**
 * Checks if transaction's gas price is above the cap for block and transaction validations
 */
public enum TxGasPriceCap {

    FOR_BLOCK(100),
    FOR_TRANSACTION(80);

    @VisibleForTesting
    public final BigInteger timesMinGasPrice;

    TxGasPriceCap(long timesMinGasPrice) {
        this.timesMinGasPrice = BigInteger.valueOf(timesMinGasPrice);
    }

    public boolean isSurpassed(Transaction tx, Coin blockMinGasPrice) {
        if (tx instanceof RemascTransaction) {
            return false;
        }

        if (blockMinGasPrice.equals(Coin.ZERO)) {
            return false;
        }

        Coin gasPriceCap = blockMinGasPrice.multiply(this.timesMinGasPrice);
        return tx.getGasPrice().compareTo(gasPriceCap) > 0;
    }

}