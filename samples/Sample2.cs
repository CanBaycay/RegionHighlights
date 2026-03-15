namespace SomeNamespace
{
    public class SomeClassWithNestedRegions
    {
        #region Parent region

        // Code here

        #region Nested region 1

        // Code here

        #endregion

        #region Nested region 2, but closed

        // Code here

        #endregion

        #endregion

        #region Parent contains nested region, but closed

        // Code here

        #region Nested hidden region

        // Code here

        #endregion

        #endregion
    }
}