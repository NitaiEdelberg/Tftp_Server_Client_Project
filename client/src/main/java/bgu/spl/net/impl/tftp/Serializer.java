package bgu.spl.net.impl.tftp;

public class Serializer {
	public enum Opcodes {
		RRQ(1, 1),
		WRQ(2, 1),
		DATA(3, 3),
		ACK(4, 1),
		ERROR(5, 2),
		DIRQ(6, 0),
		LOGRQ(7, 1),
		DELRQ(9, 1),
		BCAST(9, 2),
		DISC(10, 0);

		private final int associatedValue;
		private final int numberOfArguments;

		Opcodes(int value, int numberOfArguments) {
			this.associatedValue = value;
			this.numberOfArguments = numberOfArguments;
		}

		public int getAssociatedValue() {
			return associatedValue;
		}
	}
}
