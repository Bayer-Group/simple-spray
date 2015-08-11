---
layout: post
title: "Building a simple Spray application"
subtitle: "Making first contact with Spray as painless as possible"
header-img: "img/mon-field_rows.jpg"
author: Scott MacDonald 
githubProfile : "samus42"
avatarUrl : "https://avatars1.githubusercontent.com/u/1211796?v=3&s=460"
tags: [open source, spray, scala]
---


You've probably heard people talk about Spray by now; maybe you're
even using it for JSON serialization.  Spray also provides a
lightweight server package to allow you easily create REST services.
The hardest part of getting started with any new technology is getting
past that initial contact of setup and concepts.  I'll try to reduce
that hurdle by walking you through a simple app that hits most of the
features you might regularly use.

Because the spray documentation isn't great about saying what
dependencies you need, or imports for that matter, I'll be including
them so you don't have to guess.  Also, the final source code is at
[simple-spray](https://github.com/MonsantoCo/simple-spray).

## Setting up the project's build.sbt

Your build.sbt are going to need the following dependencies

```scala
val akka = "2.3.9"

val spray = "1.3.2"

resolvers += Resolver.url("TypeSafe Ivy releases", url("http://dl.bintray.com/typesafe/ivy-releases/"))(Resolver.ivyStylePatterns)

libraryDependencies ++=
    Seq(
        // -- Logging --
        "ch.qos.logback" % "logback-classic" % "1.1.2",
        "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
        // -- Akka --
        "com.typesafe.akka" %% "akka-testkit" % akka % "test",
        "com.typesafe.akka" %% "akka-actor" % akka,
        "com.typesafe.akka" %% "akka-slf4j" % akka,
        // -- Spray --
        "io.spray" %% "spray-routing" % spray,
        "io.spray" %% "spray-client" % spray,
        "io.spray" %% "spray-testkit" % spray % "test",
        // -- json --
        "io.spray" %% "spray-json" % "1.3.1",
        // -- config --
        "com.typesafe" % "config" % "1.2.1",
        // -- testing --
        "org.scalatest" %% "scalatest" % "2.2.1" % "test"
    )
```

## Creating a bare bones service

Spray utilizes Akka Actors to receive the service calls, a pattern
that is used to setup the routes is to create Scala Traits.  So a
framework would look like this:

```scala
import akka.actor.Actor
import spray.routing.HttpService

class SampleServiceActor extends Actor with SampleRoute {
    def actorRefFactory = context
    def receive = runRoute(route)
}

trait SampleRoute extends HttpService {
    val route = {
        get {
            complete("I exist!")
        }
    }
}
```

As you can see, the trait contains a *route* value that get's passed
into the runRoute method.  You don't have to do it this way, but using
traits can be a good way of organizing your routes.

So we have a service setup, let's actually run it and try it out.  We
need one more object, a Main class to bind to the port.

```scala
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

object Main extends App {
    implicit val system = ActorSystem("simple-service")
    val service = system.actorOf(Props[SampleServiceActor], "simple-service")

    //If we're on cloud foundry, get's the host/port from the env vars
    lazy val host = Option(System.getenv("VCAP_APP_HOST")).getOrElse("localhost")
    lazy val port = Option(System.getenv("VCAP_APP_PORT")).getOrElse("8080").toInt

	IO(Http) ! Http.Bind(service, host, port = port)
}
```

Our main class set's up the Akka ActorSystem, and attaches our
SampleServiceActor to it.  We then bind the service to the host and
port (I put in code to show how to get it from Cloud Foundry).

At this point in IntelliJ you should be able to run the Main class.
It should just take a second to launch, then in your browser you can
look at http://localhost:8080/ and see the message we returned.

So now we have the basic framework up, let's make it more interesting
by....

## Adding paths/routes

So currently we're just hitting the root of the service, let's add a
subresource called *stuff*.

```scala
val route = {
        get {
            complete("I exist!")
        } ~
          get {
              path("stuff") {
                  complete("That's my stuff!")
              }
          }
    }
```
    
The *path* operation allows us to name a route, and the tilde operator
allows you to chain things together (it's a common usage in the Spray
libraries).  In theory http://localhost:8080/stuff should show us our
new message.  But instead we still see the *I exist!* message.

What's going on is Spray goes down the list one by one until it finds
a ***complete***.  Since the root has a *get* on it, it resolves that
first.  By reordering we can fix this problem

```scala
val route = {
        get {
            path("stuff") {
                complete("That's my stuff!")
            }
        } ~ get {
            complete("I exist!")
        }
    }
```

## Doing a POST

So I'm sure you've noticed the *get* by now that signifies which verb
we want to use.  The other verbs (post/put/delete/etc) are also
supported. Let's do a POST to the *stuff* resource.

```scala
val route = {
        get {
            path("stuff") {
                complete("That's my stuff!")
            }
        } ~
          post {
              path("stuff") {
                  complete("stuff posted!")
              }
          } ~
          get {
              complete("I exist!")
          }
    }
```
    
Obviously we're not really getting any data (that will come in the
next section), but we can just do a POST and see this message.  There
is a problem though, we've got some repetition since we do a
*path("stuff")* twice.

It turns out Spray is very flexible about how you nest things!  So we
can easily do some code reduction by putting the *path* first and have
it contain the *get* and *post*.

```scala
val route = {
        path("stuff") {
            get {
                complete("That's my stuff!")
            } ~
              post {
                  complete("stuff posted!")
              }
        } ~
          get {
              complete("I exist!")
          }
    }
```

That's better!  Note the tilde chaining the get & post together.

## Reading and returning JSON

So we're ready to deal with some data, so we'll change our *get* and
*post* to use JSON objects.

For our purposes, the object is going to be really simple. I'll also
create the format to use *spray.json*'s serialization.

```scala
import spray.json.DefaultJsonProtocol

case class Stuff(id: Int, data: String)

object Stuff extends DefaultJsonProtocol {
    implicit val stuffFormat = jsonFormat2(Stuff.apply)
}
```

Now we'll have our *get* return some fake data.  I'll list the whole trait again so there's no confusion.

```scala
trait SampleRoute extends HttpService {
    import spray.httpx.SprayJsonSupport._
    import Stuff._
    import spray.http.MediaTypes

    val route = {
        path("stuff") {
            respondWithMediaType(MediaTypes.`application/json`) {
                get {
                    complete(Stuff(1, "my stuff"))
                } ~
                  post {
                      complete("stuff posted!")
                  }
            }
        } ~
          get {
              complete("I exist!")
          }
    }
}
```

The *complete* call takes the case class **stuff** and automatically takes care of creating the JSON for it.

Please note the function *respondWithMediaType*. You can use this to
go a different route if someone wants XML vs JSON.  I recommend that
you always put this on anything that returns JSON so if browser
demands that type, your service doesn't erroneously return that it
can't do that.

Now that we're returning data, let's modify our *post* call to read
data.  We'll return the data with 100 added to the id, and the text
*posted* added to the data.

```scala
post {
	entity(as[Stuff]) { stuff =>
    	complete(Stuff(stuff.id + 100, stuff.data + " posted"))
    }
}
```

Note another nesting done here with the *entity(as[Stuff])* which will
deserialize the JSON into an object variable provided as a parameter
to the expression block.

## Routes with a depth of greater than 1

So you might decided to do a route *junk/mine* and *junk/yours*, and
your first thought would be to do *path("junk/mine")*.  While
intuitive, that unfortunately will not work.  When you go into more
complex paths, Spray utilizes the *pathPrefix* and *pathEnd*
directives.

```scala
 pathPrefix("junk") {
 	pathPrefix("mine") {
    	pathEnd {
        	get {
            	complete("MINE!")
            }
         }
    } ~ pathPrefix("yours") {
    	pathEnd {
        	get {
            	complete("YOURS!")
            }
        }
    }
}
```

It gets very 'nesty' but allows you a lot of flexibility in creating
your routes.  There's a great more you can do with this, but we'll
leave that for a future blog post or the Spray documentation.  We are
**trying** to keep this simple.

## Getting query parameters

At some point, you're going to want to handle query parameters, both
required and optional.  We'll create a route named *params* that will
take in a required parameter *req* and an optional parameter *opt*.

```scala
path("params") {
	get {
		parameters('req, 'opt.?) { (req, opt) =>
			complete(s"Req: $req, Opt: $opt")
		}
	}
} 
```

Notice the single tick declaration, and for opt the .?. The .?
signifies the parameter is optional. You can list as many parameters
as you want, and they will be provided to the expression as variables
(the variable names don't need to be named the same as the query
params).  If you run this and go to
http://localhost:8080/params?req=hi&opt=bye your response should be

```
Req: hi, Opt: Some(bye)
```

So you can see the optional parameter translates to a Scala Option type.

Now hit the url without any query parameters
(http://localhost:8080/params).  You'll notice the message is our root
level get *I exist!*. This is because it didn't match against the
required parameters so it fell through to our root *get*.  However, if
you comment out our root *get* you'll see the following message:

```
Request is missing required query parameter 'req'
```

Which is much more useful.  The lesson here is to be careful about how
things can flow through and design your routes appropriately.

## Getting headers

Similarly to getting parameters, you can retrieve headers through the
*headerValueByName* directive.

```scala
path("headers") {
	get {
		headerValueByName("ct-remote-user") { userId =>
			complete(userId)
		}
	}
}
```

You also can get an optional header through the
optionalHeaderValueByName directive that will return an Option.

## Returning futures instead of data

Finally, you might have your service call a reactive framework like
Slick 3, or your worker might return a Scala Future.  Luckily, the
*complete* call handles this without any problems.

```scala
path("reactive") {
	get {
		complete(future { "I'm reactive!" })
	}
}
```

So you can make your code as reactive as you want and never have to do
an Await.result!

## Packaging your app for deployment

Now we have our services, we're ready to deploy them.  For this we
need to instruct SBT on how to package the distribution.  In the
build.sbt I started with, there was this line

```scala
lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)
```

That uses the sbt-native-packager plugin provided by typesafe.  In
your project/plugins.sbt, you'll want to add the following

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0")
```

If you run **sbt dist** you'll end up with a zip file under
target/universal which you can send to the deployment mechanism of
your choice (for instance, Cloud Foundry).

## Finally done

As I stated above (ok, WAY above), the code is at
[simple-spray](https://github.com/MonsantoCo/simple-spray).  Feel free
to fork it into your own repository and experiment with it.  But the
building blocks I've given you should be able to handle a great deal
of your requests.

I'll follow up later with more advanced spray features focusing on
pathing and unit testing your spray services.
