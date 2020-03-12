# ByteBuffer

## Allocation

```
ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
```

## Switchs entre les modes 

Mode lecture: 

```
buff.flip();
```

Mode écriture :

```
buff.compact();
```

# Charset

## Allocation

```
Charset cs = Charset.forName(<charsetName>);
// or
Charset cs = StandardCharsets.<charsetName>
```

## Encodage

```
cs.encode(String msg): ByteBuffer;
```

Renvoie un ByteBuffer contenant la chaine encodée avec le charset

## Décodage

```
cs.decode(ByteBuffer bb): CharBuffer;
```

Renvoie un CharBuffer sur lequel on peut faire ensuite un toString() 

# SocketAddress

## Allocation

```
SockerAddress server = new InetSocketAddress(String host, int port);
```


# DatagramChannel

* Principe = boite aux lettres

## Allocation

```
DatagramChannel dc = DatagramChannel.open;
dc.bind(null);
```

## Fermeture

Penser à fermer le DatagramChannel

```
dc.close()
```

## Réception

```
dc.receive(buff): SockerAddress;
```

Renvoie l'adresse du serveur qui a envoyé les données.
Stocke ces données dans le buffer passé en paramètre.

## Envoi

```
dc.send(ByteBuffer cs.encode(msg), SocketAddress server);
```


