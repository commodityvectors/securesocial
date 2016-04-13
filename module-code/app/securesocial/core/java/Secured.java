/**
 * Copyright 2012-2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package securesocial.core.java;

import play.libs.F;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import scala.Option;
import scala.runtime.BoxedUnit;
import securesocial.core.RuntimeEnvironment;
import securesocial.core.authenticator.Authenticator;

import javax.inject.Inject;
import java.util.function.Function;

/**
 * Protects an action with SecureSocial
 * <p>
 * Sample usage:
 *
 * @SecuredAction public static Result index() {
 * User user = (User) ctx().args.get(SecureSocial.USER_KEY);
 * return ok("Hello " + user.displayName);
 * }
 */
public class Secured extends Action<SecuredAction> {

    private RuntimeEnvironment env;
    private Authorization authorizationInstance;
    private SecuredActionResponses responses;

    @Inject
    public Secured(RuntimeEnvironment env) throws Throwable {
        this.env = env;
    }

    static void initEnv(RuntimeEnvironment env) throws IllegalAccessException, InstantiationException {
        if (SecureSocial.env() == null) {
            Http.Context.current().args.put("securesocial-env", env);
        }
    }


    @Override
    public F.Promise<Result> call(final Http.Context ctx) {
        Exception exception;
        try {
            initEnv(env);
            authorizationInstance = configuration.authorization().newInstance();
            responses = configuration.responses().newInstance();
            return F.Promise.wrap(env.authenticatorService().fromRequest(ctx._requestHeader())).flatMap(
                    new Function<Option<Authenticator<Object>>, F.Promise<Result>>() {
                        @Override
                        public F.Promise<Result> apply(Option<Authenticator<Object>> authenticatorOption) {
                            if (authenticatorOption.isDefined() && authenticatorOption.get().isValid()) {
                                final Authenticator authenticator = authenticatorOption.get();
                                Object user = authenticator.user();
                                if (authorizationInstance.isAuthorized(user, configuration.params())) {
                                    return F.Promise.wrap(authenticator.touch()).flatMap(new Function<Authenticator, F.Promise<Result>>() {
                                        @Override
                                        public F.Promise<Result> apply(Authenticator touched) {
                                            ctx.args.put(SecureSocial.USER_KEY, touched.user());
                                            return F.Promise.wrap(touched.touching(ctx)).flatMap(new Function<scala.runtime.BoxedUnit, F.Promise<Result>>() {
                                                @Override
                                                public F.Promise<Result> apply(scala.runtime.BoxedUnit unit) {
                                                    return F.Promise.wrap(delegate.call(ctx));
                                                }
                                            });
                                        }
                                    });
                                } else {
                                    return responses.notAuthorizedResult(ctx);
                                }
                            } else {
                                if (authenticatorOption.isDefined()) {
                                    return F.Promise.wrap(authenticatorOption.get().discarding(ctx)).flatMap(
                                            new Function<BoxedUnit, F.Promise<Result>>() {
                                                @Override
                                                public F.Promise<Result> apply(BoxedUnit unit) {
                                                    return responses.notAuthenticatedResult(ctx);
                                                }
                                            }
                                    );
                                }
                                return responses.notAuthenticatedResult(ctx);
                            }
                        }
                    }
            );
        } catch (IllegalAccessException | InstantiationException e) {
            exception = e;
        }
        throw new RuntimeException(exception);
    }
}
