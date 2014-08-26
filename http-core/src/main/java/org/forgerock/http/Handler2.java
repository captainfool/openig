/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems Inc.
 * Portions Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.http;

/**
 * TODO: proposed new interface for synchronous handlers.
 * <p>
 * Handles an HTTP exchange request by producing an associated response.
 */
public interface Handler2 {
    /**
     * Called to request the handler respond to the request.
     * <p>
     * A handler that doesn't hand-off an exchange to another handler downstream
     * is responsible for creating the response and returning it or by throwing
     * a {@code ResponseException}.
     *
     * @param context
     *            The request context.
     * @param request
     *            The request.
     * @return The response.
     * @throws ResponseException
     *             If an exception occurs that prevents handling of the request.
     */
    Response handle(Context context, Request request) throws ResponseException;
}