/**
 * Copyright 2011-2016 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.greenbus.msg.japi;

/**
 * ConnectionCloseListener is informed of disconnections from the message broker (expected
 * or otherwise). Callbacks come in from the underlying connection's thread so it
 * is important not to block the callback.
 *
 * Once this callback has been fired the connection is dead and all Client operations
 * will fail so this is not generally a useful mechanism to shutdown an application
 * because it will not be able to make any calls to reef during shutdown. Should only
 * be used at the "top" of the application to shutdown the application in the "nasty case".
 */
public interface ConnectionCloseListener
{

    /**
     * called when we lose connection to the broker. This means all subscriptions and clients
     * created by the Connection are dead and the application as a whole needs to be restarted
     * (if unexpected).
     *
     * @param expected True if the close was user initiated, false otherwise
     */
    void onConnectionClosed(boolean expected);

}