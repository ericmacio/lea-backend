# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
This project aims at providing a backend chat web service. It must allow the client to send a chat request and get back the result.
The service must use OpenAI chat capability to build the response to the user

## Language and framework
The coding language must be Java. the framework to be used is Java Spring AI. Model to be used are gpt-4o-mini by default.

## Security
Must use API key as well as rate limiter and CORS strategy.
I would like to add a security based on session token with limited validation time. Could you please propose a solution and document it to TOKEN-DESIGN.md


## Database
When required a in memory database will be preferred (i.e. h2database)

## Chat response
The chat response sent to the user must use streaming capability when possible