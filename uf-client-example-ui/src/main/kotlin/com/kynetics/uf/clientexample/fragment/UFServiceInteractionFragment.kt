/*
 * Copyright © 2017-2023  Kynetics  LLC
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.kynetics.uf.clientexample.fragment

import com.kynetics.uf.android.api.v1.UFServiceMessageV1

/**
 * @author Daniele Sergio
 */
interface UFServiceInteractionFragment {
    fun onMessageReceived(message: UFServiceMessageV1)
}
