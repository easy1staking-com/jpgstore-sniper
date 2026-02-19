# JPG Store Sniper

## Jpgstore Contract V3

https://github.com/jpg-store/contracts-v3


How to list and buy NFT on jpg.store with java.

# Fund 11 proposal

* java jpgstore client lib 10k $
* java jpgstore sniper lib 50k $
* java mempool evaluator

## Open CNFT Docs

https://docs.opencnft.io/operation/operation-publicv2controller_getaddresstransaction

## PSQL

Init local dev psql db

`createuser --superuser postgres`

`psql -U postgres`

Then create db:

```
CREATE USER jpgsniper PASSWORD 'password';

CREATE DATABASE jpgsniper WITH OWNER jpgsniper;
```